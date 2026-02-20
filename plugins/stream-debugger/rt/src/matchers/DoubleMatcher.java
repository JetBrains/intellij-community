// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.debugger.streams.rt.matchers;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.DoublePredicate;

/**
 * @author Shumaf Lovpache
 * This helper class is loaded by the IntelliJ IDEA stream debugger
 */
@SuppressWarnings("unused")
public class DoubleMatcher implements DoublePredicate {
  private final Map<Integer, Object> beforeMapping;
  private final Map<Integer, Object> afterMapping;
  private final AtomicInteger time;
  private final DoublePredicate inner;

  public DoubleMatcher(Map<Integer, Object> beforeMapping, Map<Integer, Object> afterMapping, AtomicInteger time, DoublePredicate inner) {
    this.beforeMapping = beforeMapping;
    this.afterMapping = afterMapping;
    this.time = time;
    this.inner = inner;
  }

  @Override
  public boolean test(double value) {
    int t = time.get();
    beforeMapping.put(t, value);
    if (inner.test(value)) {
      afterMapping.put(t, value);
      return true;
    }

    return false;
  }
}
