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
 package com.indeed.flamdex.api;

/**
 * @author jplaisance
 */
public final class GenericIntTermDocIterator extends GenericTermDocIterator implements IntTermDocIterator {
    private final IntTermIterator termIterator;

    public GenericIntTermDocIterator(final IntTermIterator termIterator, final DocIdStream docIdStream) {
        super(termIterator, docIdStream);
        this.termIterator = termIterator;
    }

    @Override
    public long term() {
        return termIterator.term();
    }

    @Override
    public int nextDocs(final int[] docIdBuffer) {
        return fillDocIdBuffer(docIdBuffer);
    }
}

