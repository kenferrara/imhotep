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
 package com.indeed.imhotep.io;

import java.io.IOException;
import java.io.OutputStream;

/**
 * @author jsgroth
 */
public final class SubOutputStream extends OutputStream {
    private static final int BLOCK_SIZE = 65536;

    private final OutputStream os;

    private final byte[] bytes;
    private int currentBlockSize;

    public SubOutputStream(OutputStream os) {
        this.os = os;
        bytes = new byte[BLOCK_SIZE];
    }

    @Override
    public void write(int b) throws IOException {
        bytes[currentBlockSize++] = (byte)b;
        if (currentBlockSize == BLOCK_SIZE) {
            writeBlock();
        }
    }

    private void writeBlock() throws IOException {
        Streams.writeInt(os, currentBlockSize);
        os.write(bytes, 0, currentBlockSize);
        currentBlockSize = 0;
    }

    // note that this method does NOT close the underlying OutputStream
    @Override    
    public void close() throws IOException {
        if (currentBlockSize > 0) {
            writeBlock();
        }
        Streams.writeInt(os, 0);
        os.flush();
    }
}
