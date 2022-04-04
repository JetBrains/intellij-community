// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.stream.rt.java.collectors;

/**
 * @author Shumaf Lovpache
 * This helper class is loaded by the IntelliJ IDEA stream debugger
 */
class LongCollector implements java.util.function.LongConsumer {
    private final java.util.Map<Integer, Object> storage;
    private final java.util.concurrent.atomic.AtomicInteger time;

    LongCollector(java.util.Map<Integer, Object> storage, java.util.concurrent.atomic.AtomicInteger time) {
        this.storage = storage;
        this.time = time;
    }

    @Override
    public void accept(long t) {
        storage.put(time.incrementAndGet(), t);
    }
}