// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.debugger.streams.rt;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * A {@link Map.Entry} wrapper / factory for the stream debugger's distinct-by-entry operations.
 *
 * <p>A <em>factory instance</em> is created via {@link #keys()} or {@link #values()} and passed to
 * {@code stream.map(factory)}: its {@link #apply} method wraps each incoming {@link Map.Entry} in a
 * per-element instance that shares the factory's {@link #capturedKeys} list.
 *
 * <p>When {@code distinctKeys()}/{@code distinctValues()} calls {@link #getKey()}/{@link #getValue()}
 * on a per-element wrapper, the accessed projection is appended to {@code capturedKeys} on the
 * factory.  This lets the debugger reconstruct the equivalence-class mapping without re-invoking
 * {@code getKey()}/{@code getValue()} post-execution (safe for side-effecting entry implementations).
 */
@SuppressWarnings("unused")
public final class EntryKeyCapturingWrapper implements Map.Entry<Object, Object>, Function<Object, Object> {
  private final Map.Entry<?, ?> original;
  // Accumulates projections in stream order
  public final List<Object> capturedKeys;
  private final boolean byKey;

  public static EntryKeyCapturingWrapper keys() {
    return new EntryKeyCapturingWrapper(true);
  }

  public static EntryKeyCapturingWrapper values() {
    return new EntryKeyCapturingWrapper(false);
  }

  private EntryKeyCapturingWrapper(boolean byKey) {
    this.original = null;
    this.capturedKeys = new ArrayList<>();
    this.byKey = byKey;
  }

  private EntryKeyCapturingWrapper(Map.Entry<?, ?> original, List<Object> capturedKeys, boolean byKey) {
    this.original = original;
    this.capturedKeys = capturedKeys;
    this.byKey = byKey;
  }

  @Override
  public Object apply(Object entry) {
    return new EntryKeyCapturingWrapper((Map.Entry<?, ?>) entry, capturedKeys, byKey);
  }

  @Override
  public Object getKey() {
    Object key = original.getKey();
    if (byKey) capturedKeys.add(key);
    return key;
  }

  @Override
  public Object getValue() {
    Object value = original.getValue();
    if (!byKey) capturedKeys.add(value);
    return value;
  }

  @SuppressWarnings("unchecked")
  @Override
  public Object setValue(Object value) {
    return ((Map.Entry<Object, Object>) original).setValue(value);
  }
}
