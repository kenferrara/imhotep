package com.indeed.flamdex.dynamic;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Closer;
import com.indeed.flamdex.MemoryFlamdex;
import com.indeed.flamdex.api.DocIdStream;
import com.indeed.flamdex.api.IntTermIterator;
import com.indeed.flamdex.api.StringTermIterator;
import com.indeed.flamdex.datastruct.FastBitSet;
import com.indeed.flamdex.dynamic.locks.MultiThreadFileLockUtil;
import com.indeed.flamdex.dynamic.locks.MultiThreadLock;
import com.indeed.flamdex.query.BooleanOp;
import com.indeed.flamdex.query.Query;
import com.indeed.flamdex.search.FlamdexSearcher;
import com.indeed.flamdex.simple.SimpleFlamdexWriter;
import com.indeed.flamdex.writer.FlamdexDocument;
import com.indeed.flamdex.writer.IntFieldWriter;
import com.indeed.flamdex.writer.StringFieldWriter;
import com.indeed.util.core.io.Closeables2;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.apache.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * @author michihiko
 */
public class DynamicFlamdexDocWriter implements DeletableFlamdexDocWriter {
    private static final Logger LOG = Logger.getLogger(DynamicFlamdexDocWriter.class);

    private static final int DOC_ID_BUFFER_SIZE = 128;
    private static final String WRITER_LOCK = "writer.lock";

    private final MultiThreadLock writerLock;
    private final DynamicFlamdexMerger merger;
    private final Path shardDirectory;
    private MemoryFlamdex memoryFlamdex;
    private FlamdexSearcher flamdexSearcher;
    private final IntSet removedDocIds;
    private final List<Query> removeQueries;
    private final int[] docIdBuf = new int[DOC_ID_BUFFER_SIZE];

    // Don't merge at all
    public DynamicFlamdexDocWriter(@Nonnull final Path shardDirectory) throws IOException {
        this.shardDirectory = shardDirectory;
        this.writerLock = MultiThreadFileLockUtil.writeLock(this.shardDirectory, WRITER_LOCK);
        this.merger = null;
        this.removedDocIds = new IntOpenHashSet();
        this.removeQueries = new ArrayList<>();
        renew();
    }

    public DynamicFlamdexDocWriter(@Nonnull final Path shardDirectory, @Nonnull final MergeStrategy mergeStrategy, @Nonnull final ExecutorService executorService) throws IOException {
        this.shardDirectory = shardDirectory;
        this.writerLock = MultiThreadFileLockUtil.writeLock(this.shardDirectory, WRITER_LOCK);
        this.merger = new DynamicFlamdexMerger(this.shardDirectory, mergeStrategy, executorService);
        this.removedDocIds = new IntOpenHashSet();
        this.removeQueries = new ArrayList<>();
        renew();
    }

    private void renew() throws IOException {
        if (this.memoryFlamdex != null) {
            this.memoryFlamdex.close();
        }
        this.memoryFlamdex = new MemoryFlamdex();
        this.flamdexSearcher = new FlamdexSearcher(this.memoryFlamdex);
        this.removedDocIds.clear();
        this.removeQueries.clear();
    }

    private void compactCurrentSegment() throws IOException {
        if (this.removedDocIds.isEmpty()) {
            return;
        }
        final int numDocs = this.memoryFlamdex.getNumDocs();
        final int[] newDocId = new int[numDocs];
        for (final IntIterator iterator = this.removedDocIds.iterator(); iterator.hasNext(); ) {
            newDocId[iterator.nextInt()] = -1;
        }
        int nextNumDocs = 0;
        for (int i = 0; i < numDocs; ++i) {
            if (newDocId[i] == 0) {
                newDocId[i] = nextNumDocs++;
            }
        }

        final MemoryFlamdex nextWriter = new MemoryFlamdex();
        nextWriter.setNumDocs(nextNumDocs);

        try (final DocIdStream docIdStream = this.memoryFlamdex.getDocIdStream()) {
            for (final String field : this.memoryFlamdex.getIntFields()) {
                try (
                        final IntFieldWriter intFieldWriter = nextWriter.getIntFieldWriter(field);
                        final IntTermIterator intTermIterator = this.memoryFlamdex.getIntTermIterator(field)
                ) {
                    while (intTermIterator.next()) {
                        intFieldWriter.nextTerm(intTermIterator.term());
                        docIdStream.reset(intTermIterator);
                        while (true) {
                            final int num = docIdStream.fillDocIdBuffer(this.docIdBuf);
                            for (int i = 0; i < num; ++i) {
                                final int docId = newDocId[this.docIdBuf[i]];
                                if (docId >= 0) {
                                    intFieldWriter.nextDoc(docId);
                                }
                            }
                            if (num < this.docIdBuf.length) {
                                break;
                            }
                        }
                    }
                }
            }
            for (final String field : this.memoryFlamdex.getStringFields()) {
                try (
                        final StringFieldWriter stringFieldWriter = nextWriter.getStringFieldWriter(field);
                        final StringTermIterator stringTermIterator = this.memoryFlamdex.getStringTermIterator(field)
                ) {
                    while (stringTermIterator.next()) {
                        stringFieldWriter.nextTerm(stringTermIterator.term());
                        docIdStream.reset(stringTermIterator);
                        while (true) {
                            final int num = docIdStream.fillDocIdBuffer(this.docIdBuf);
                            for (int i = 0; i < num; ++i) {
                                final int docId = newDocId[this.docIdBuf[i]];
                                if (docId >= 0) {
                                    stringFieldWriter.nextDoc(docId);
                                }
                            }
                            if (num < this.docIdBuf.length) {
                                break;
                            }
                        }
                    }
                }
            }
        } catch (final IOException e) {
            Closeables2.closeQuietly(this.memoryFlamdex, LOG);
            Closeables2.closeQuietly(nextWriter, LOG);
            throw e;
        }
        this.memoryFlamdex.close();
        this.removedDocIds.clear();
        this.memoryFlamdex = nextWriter;
        this.flamdexSearcher = new FlamdexSearcher(this.memoryFlamdex);
    }

    private static class DeleteOnClose implements Closeable {
        private final Path path;

        private DeleteOnClose(final Path path) {
            this.path = path;
        }

        @Override
        public void close() throws IOException {
            Files.walkFileTree(this.path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(@Nonnull final Path file, @Nonnull final BasicFileAttributes attributes) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(@Nonnull final Path dir, @Nullable final IOException exception) throws IOException {
                    if (exception != null) {
                        throw exception;
                    }
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    private void buildSegment() throws IOException {
        try (
                final Closer closer = Closer.create()
        ) {
            final Closer closerOnFailure = Closer.create();

            // While this lock is taken, merger stops to add other segment,
            // and merger is responsible for handling currentDeleted-id data for existing segments.
            // Additionally, because this 'build' happens an single-thread single-process,
            // there are no other thread that modifies segment metadata / currentDeleted-ids.
            final MultiThreadLock lock = closer.register(DynamicFlamdexMetadataUtil.acquireSegmentBuildLock(this.shardDirectory));
            try {
                final String segmentName = DynamicFlamdexSegmentUtil.generateSegmentName();
                final Path segmentDirectory = this.shardDirectory.resolve(segmentName);
                try (final SimpleFlamdexWriter flamdexWriter = new SimpleFlamdexWriter(segmentDirectory, this.memoryFlamdex.getNumDocs())) {
                    SimpleFlamdexWriter.writeFlamdex(this.memoryFlamdex, flamdexWriter);
                }
                closerOnFailure.register(new DeleteOnClose(segmentDirectory));

                final SegmentInfo newSegmentInfo;
                if (this.removedDocIds.isEmpty()) {
                    newSegmentInfo = new SegmentInfo(this.shardDirectory, segmentName, this.memoryFlamdex.getNumDocs(), Optional.<String>absent());
                } else {
                    final FastBitSet tombstoneSet = new FastBitSet(this.memoryFlamdex.getNumDocs());
                    for (final IntIterator iterator = this.removedDocIds.iterator(); iterator.hasNext(); ) {
                        tombstoneSet.set(iterator.nextInt());
                    }
                    final String tombstoneSetFileName = DynamicFlamdexSegmentUtil.outputTombstoneSet(segmentDirectory, segmentName, tombstoneSet);
                    newSegmentInfo = new SegmentInfo(this.shardDirectory, segmentName, this.memoryFlamdex.getNumDocs(), Optional.of(tombstoneSetFileName));
                }

                if (removeQueries.isEmpty()) {
                    DynamicFlamdexMetadataUtil.modifyMetadata(shardDirectory, new Function<List<SegmentInfo>, List<SegmentInfo>>() {
                        @Override
                        public List<SegmentInfo> apply(final List<SegmentInfo> segmentInfos) {
                            segmentInfos.add(newSegmentInfo);
                            return segmentInfos;
                        }
                    });
                } else {
                    final ImmutableList.Builder<SegmentInfo> newSegments = ImmutableList.builder();
                    newSegments.add(newSegmentInfo);

                    final Query query = Query.newBooleanQuery(BooleanOp.OR, removeQueries);
                    final List<SegmentReader> segmentReaders = DynamicFlamdexMetadataUtil.openSegmentReaders(this.shardDirectory);
                    for (final SegmentReader segmentReader : segmentReaders) {
                        closer.register(segmentReader);
                    }

                    for (final SegmentReader segmentReader : segmentReaders) {
                        final Optional<FastBitSet> updatedTombstoneSet = segmentReader.getUpdatedTombstoneSet(query);
                        if (updatedTombstoneSet.isPresent()) {
                            final String tombstoneSetFileName = DynamicFlamdexSegmentUtil.outputTombstoneSet(segmentReader.getDirectory(), segmentName, updatedTombstoneSet.get());
                            final SegmentInfo segmentInfo = new SegmentInfo(this.shardDirectory, segmentReader.getSegmentInfo().getName(), segmentReader.maxNumDocs(), Optional.of(tombstoneSetFileName));
                            //noinspection OptionalGetWithoutIsPresent
                            closerOnFailure.register(new DeleteOnClose(segmentInfo.getTombstoneSetPath().get()));
                            newSegments.add(segmentInfo);
                        } else {
                            newSegments.add(segmentReader.getSegmentInfo());
                        }
                    }
                    DynamicFlamdexMetadataUtil.modifyMetadata(shardDirectory, new Function<List<SegmentInfo>, List<SegmentInfo>>() {
                        @Override
                        public List<SegmentInfo> apply(final List<SegmentInfo> segmentInfos) {
                            return newSegments.build();
                        }
                    });
                }
            } catch (final IOException e) {
                Closeables2.closeQuietly(closerOnFailure, LOG);
                throw closer.rethrow(e);
            }
        }

    }

    public void flush() throws IOException {
        flush(true);
    }

    public void flush(final boolean compact) throws IOException {
        if (compact) {
            compactCurrentSegment();
        }
        if ((this.memoryFlamdex.getNumDocs() == 0) && this.removeQueries.isEmpty()) {
            return;
        }
        buildSegment();
        if (merger != null) {
            merger.updated();
        }
        renew();
    }

    @Override
    public void close() throws IOException {
        flush();
        Closeables2.closeAll(LOG, this.memoryFlamdex, this.merger, writerLock);
    }

    @Override
    public void addDocument(@Nonnull final FlamdexDocument doc) throws IOException {
        this.memoryFlamdex.addDocument(doc);
    }

    @Override
    public void deleteDocuments(@Nonnull final Query query) throws IOException {
        removeQueries.add(query);
        final FastBitSet removed = flamdexSearcher.search(query);
        for (final FastBitSet.IntIterator iterator = removed.iterator(); iterator.next(); ) {
            this.removedDocIds.add(iterator.getValue());
        }
    }
}
