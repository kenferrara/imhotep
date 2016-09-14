package com.indeed.imhotep.service;

import com.google.common.base.Function;
import com.google.common.base.Supplier;
import com.google.common.collect.FluentIterable;
import com.indeed.imhotep.ShardDir;
import com.indeed.imhotep.io.NioPathUtil;
import com.indeed.imhotep.shardmaster.ShardMaster;
import com.indeed.imhotep.shardmaster.protobuf.AssignedShard;
import com.indeed.util.core.Pair;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Iterator;

/**
 * @author kenh
 */

public class ShardMasterShardDirIterator implements  ShardDirIterator {
    private final Supplier<ShardMaster> shardMasterSupplier;
    private final String node;

    public ShardMasterShardDirIterator(final Supplier<ShardMaster> shardMasterSupplier, final String node) {
        this.shardMasterSupplier = shardMasterSupplier;
        this.node = node;
    }

    @Override
    public Iterator<Pair<String, ShardDir>> iterator() {
        final Iterable<AssignedShard> assignments;
        try {
            final ShardMaster shardMaster = shardMasterSupplier.get();
            if (shardMaster == null) {
                return Collections.<Pair<String, ShardDir>>emptyList().iterator();
            }
            assignments = shardMaster.getAssignments(node);
        } catch (final IOException e) {
            throw new IllegalStateException("Failed to get shard assignment for " + node, e);
        }

        return FluentIterable.from(assignments).transform(new Function<AssignedShard, Pair<String, ShardDir>>() {
            @Override
            public Pair<String, ShardDir> apply(final AssignedShard shard) {
                final Path shardPath;
                try {
                    shardPath = NioPathUtil.get(shard.getShardPath());
                } catch (final URISyntaxException e) {
                    throw new IllegalStateException("Unexpected path " + shard.getShardPath(), e);
                }
                return Pair.of(shard.getDataset(), new ShardDir(shardPath));
            }
        }).iterator();
    }
}
