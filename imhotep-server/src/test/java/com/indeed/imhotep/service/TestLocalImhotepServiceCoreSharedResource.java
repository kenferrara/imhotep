/*
 * Copyright (C) 2014 Indeed Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
 package com.indeed.imhotep.service;

import com.indeed.flamdex.api.FlamdexReader;
import com.indeed.flamdex.reader.MockFlamdexReader;
import com.indeed.imhotep.api.ImhotepOutOfMemoryException;
import com.indeed.util.core.shell.PosixFileOperations;
import junit.framework.TestCase;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author jsgroth
 *
 * some of these tests are reliant on the implementation of
 * LocalImhotepServiceCore.updateShards() if that method changes significantly
 * then these tests will very likely break
 */
public class TestLocalImhotepServiceCoreSharedResource extends TestCase {
    private static final long TIMEOUT = 5000L;

    private Path tempDir;
    private Path directory;
    private Path optDirectory;

    @Override
    protected void setUp() throws Exception {
        tempDir = Files.createTempDirectory(this.getClass().getName());
        Path datasetDir = Files.createDirectory(tempDir.resolve("dataset"));
        Path shardDir = Files.createDirectory(datasetDir.resolve("shard"));
        Path optDir = Files.createDirectory(tempDir.resolve("temp"));

        directory = tempDir;
        optDirectory = optDir;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    protected void tearDown() throws Exception {
        PosixFileOperations.rmrf(tempDir);
    }

    @Test
    public void testNoDoubleClose() throws IOException, ImhotepOutOfMemoryException {
        FlamdexReaderSource factory = new FlamdexReaderSource() {
            @Override
            public FlamdexReader openReader(Path directory) throws IOException {
                return new MockFlamdexReader(Arrays.asList("if1"),
                                             Arrays.asList("sf1"),
                                             Arrays.asList("if1"), 10) {
                    @Override
                    public long memoryRequired(String metric) {
                        return Long.MAX_VALUE;
                    }
                };
            }
        };

        LocalImhotepServiceCore service = new LocalImhotepServiceCore(directory,
                                                                      optDirectory,
                                                                      1024L * 1024 * 1024,
                                                                      false,
                                                                      factory,
                                                                      new LocalImhotepServiceConfig());
        String sessionId = service.handleOpenSession("dataset",
                                                     Arrays.asList("shard"),
                                                     "",
                                                     "",
                                                     0,
                                                     0,
                                                     false,
                                                     "",
                                                     null,
                                                     false,
                                                     0);
        try {
            service.handlePushStat(sessionId, "if1");
            assertTrue("pushStat didn't throw ImhotepOutOfMemory when it should have", false);
        } catch (ImhotepOutOfMemoryException e) {
            // pass
        }
        service.handleCloseSession(sessionId);
        String sessionId2 = service.handleOpenSession("dataset",
                                                      Arrays.asList("shard"),
                                                      "",
                                                      "",
                                                      0,
                                                      0,
                                                      false,
                                                      "",
                                                      null,
                                                      false,
                                                      0);
        service.handleCloseSession(sessionId2);
        service.close();
    }

    @Test
    public void testReloadCloses() throws IOException, InterruptedException {
        final AtomicBoolean closed = new AtomicBoolean(false);
        final AtomicBoolean created = new AtomicBoolean(false);
        FlamdexReaderSource factory = new FlamdexReaderSource() {
            int i = 0;

            @Override
            public FlamdexReader openReader(Path directory) throws IOException {
                while (!created.compareAndSet(false, true)) {}

                if (((i++) & 1) == 0) {
                    return new MockFlamdexReader(Arrays.asList("if1"),
                                                 Collections.<String>emptyList(),
                                                 Arrays.asList("if1"),
                                                 10) {
                        @Override
                        public void close() throws IOException {
                            while (!closed.compareAndSet(false, true)) {}
                        }
                    };
                } else {
                    return new MockFlamdexReader(Collections.<String>emptyList(),
                                                 Arrays.asList("sf1"),
                                                 Collections.<String>emptyList(),
                                                 10) {
                        @Override
                        public void close() throws IOException {
                            while (!closed.compareAndSet(false, true)) {}
                        }
                    };
                }
            }
        };
        LocalImhotepServiceCore service =
                new LocalImhotepServiceCore(
                                            directory,
                                            optDirectory,
                                            Long.MAX_VALUE,
                                            false,
                                            factory,
                                            new LocalImhotepServiceConfig().setUpdateShardsFrequencySeconds(1));

        try {
            long initial = System.currentTimeMillis();
            boolean b;
            while (!(b = created.compareAndSet(true, false)) && (System.currentTimeMillis() - initial) < TIMEOUT) {
            }
            assertTrue("first index took too long to be created", b);
            final long t = System.currentTimeMillis();
            while (!(b = closed.compareAndSet(true, false)) && (System.currentTimeMillis() - t) < TIMEOUT) {
            }
            assertTrue("close took too long", b);
        } finally {
            service.close();
        }
    }

    @Test
    public void testNoReloadNoClose() throws IOException {
        final AtomicInteger createCount = new AtomicInteger(0);
        final AtomicBoolean error = new AtomicBoolean(false);
        FlamdexReaderSource factory = new FlamdexReaderSource() {
            FlamdexReader lastOpened = null;

            @Override
            public FlamdexReader openReader(Path directory) throws IOException {
                createCount.incrementAndGet();

                return (lastOpened = new MockFlamdexReader(Collections.<String>emptyList(),
                                                           Collections.<String>emptyList(),
                                                           Collections.<String>emptyList(),
                                                           10) {
                    @Override
                    public void close() throws IOException {
                        if (lastOpened != this) {
                            error.set(true);
                        }
                    }
                });
            }
        };
        LocalImhotepServiceCore service =
                new LocalImhotepServiceCore(
                                            directory,
                                            optDirectory,
                                            Long.MAX_VALUE,
                                            false,
                                            factory,
                                            new LocalImhotepServiceConfig().setUpdateShardsFrequencySeconds(1));
        try {
            long t = System.currentTimeMillis();
            boolean b = true;
            boolean problem;
            while (!(problem = error.get())
                    && (b = createCount.get() < 1)
                    && (System.currentTimeMillis() - t) < TIMEOUT) {
            }
            assertFalse("creates took too long", b);
            assertFalse("close called on a reader that it shouldn't have been", problem);
        } finally {
            service.close();
        }
    }

    @Test
    public void testActiveNoClose() throws IOException, ImhotepOutOfMemoryException, InterruptedException {
        final AtomicBoolean sessionClosed = new AtomicBoolean(false);
        final AtomicBoolean sessionOpened = new AtomicBoolean(false);
        final AtomicBoolean error = new AtomicBoolean(false);
        final AtomicBoolean done = new AtomicBoolean(false);

        FlamdexReaderSource factory = new FlamdexReaderSource() {

            @Override
            public FlamdexReader openReader(Path directory) throws IOException {
                return new MockFlamdexReader(Arrays.asList("if1"),
                                             Arrays.asList("sf1"),
                                             Arrays.asList("if1"),
                                             10) {
                    @Override
                    public void close() throws IOException {
                        if (sessionOpened.get() && !sessionClosed.get()) {
                            error.set(true);
                        } else if (sessionOpened.get()) {
                            done.set(true);
                        }
                    }
                };
            }
        };

        LocalImhotepServiceCore service =
                new LocalImhotepServiceCore(
                                            directory,
                                            optDirectory,
                                            Long.MAX_VALUE,
                                            false,
                                            factory,
                                            new LocalImhotepServiceConfig().setUpdateShardsFrequencySeconds(1));
        try {
            String sessionId = service.handleOpenSession("dataset",
                                                         Arrays.asList("shard"),
                                                         "",
                                                         "",
                                                         0,
                                                         0,
                                                         false,
                                                         "",
                                                         null,
                                                         false,
                                                         0);
            sessionOpened.set(true);
            try {
                for (int i = 0; i < 5; ++i) {
                    Thread.sleep(1000);
                    assertFalse("reader closed while still open in a session", error.get());
                }
                sessionClosed.set(true);
            } finally {
                service.handleCloseSession(sessionId);
            }
            Thread.sleep(2000);
            assertTrue("reader was never closed", done.get());
        } finally {
            service.close();
        }
    }
}
