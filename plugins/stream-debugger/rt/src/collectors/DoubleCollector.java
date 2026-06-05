// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.java.rt.collectors;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.DoubleConsumer;

/**
 * @author Shumaf Lovpache
 * This helper class is loaded by the IntelliJ IDEA stream debugger
 */
@SuppressWarnings("unused")
class DoubleCollector implements DoubleConsumer {
    private final Map<Integer, Object> storage;
    private final AtomicInteger time;
    private final boolean tick;

    DoubleCollector(Map<Integer, Object> storage, AtomicInteger time, boolean tick) {
        this.storage = storage;
        this.time = time;
        this.tick = tick;
    }

    @Override
    public void accept(double t) {
        storage.put(tick ? time.incrementAndGet() : time.get(), t);
    }
}
