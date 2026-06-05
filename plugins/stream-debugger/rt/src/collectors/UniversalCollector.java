// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.java.rt.collectors;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;

/**
 * @author Shumaf Lovpache
 * This helper class is loaded by the IntelliJ IDEA stream debugger
 */
@SuppressWarnings("unused")
public class UniversalCollector implements IntConsumer, LongConsumer, DoubleConsumer, Consumer<Object> {
  private final Map<Integer, Object> storage;
  private final AtomicInteger time;
  private final boolean tick;

  UniversalCollector(Map<Integer, Object> storage, AtomicInteger time, boolean tick) {
    this.storage = storage;
    this.time = time;
    this.tick = tick;
  }

  @Override
  public void accept(int value) {
    accept((Object)value);
  }

  @Override
  public void accept(long value) {
    accept((Object)value);
  }

  @Override
  public void accept(double value) {
    accept((Object)value);
  }

  @Override
  public void accept(Object value) {
    int timestamp = tick ? time.incrementAndGet() : time.get();
    if (storage != null) {
      storage.put(timestamp, value);
    }
  }
}
