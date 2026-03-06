// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.debugger.streams.rt;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * A {@link Function} wrapper that records every key returned by the delegate in order.
 * Used by the stream debugger to capture keys computed by a stateful key extractor
 * during stream execution, so that the mapping can be computed correctly post-hoc
 * without re-applying the extractor.
 */
@SuppressWarnings("unused")
public final class KeyRecorder implements Function<Object, Object> {
  public final List<Object> capturedKeys = new ArrayList<>();
  private final Function<Object, Object> delegate;

  @SuppressWarnings("unchecked")
  public KeyRecorder(Function<?, ?> delegate) {
    this.delegate = (Function<Object, Object>) delegate;
  }

  @Override
  public Object apply(Object input) {
    Object key = delegate.apply(input);
    capturedKeys.add(key);
    return key;
  }
}
