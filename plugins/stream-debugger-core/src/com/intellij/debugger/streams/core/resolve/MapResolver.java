// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.core.resolve;

import com.intellij.debugger.streams.core.trace.TraceElement;
import com.intellij.debugger.streams.core.trace.TraceInfo;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Vitaliy.Bibaev
 */
public class MapResolver implements ValuesOrderResolver {
  @Override
  public @NotNull Result resolve(@NotNull TraceInfo info) {
    final Map<Integer, TraceElement> before = info.getValuesOrderBefore();
    final Map<Integer, TraceElement> after = info.getValuesOrderAfter();

    final Iterator<TraceElement> leftIterator = before.values().iterator();
    final Iterator<TraceElement> rightIterator = after.values().iterator();

    final Map<TraceElement, List<TraceElement>> forward = new LinkedHashMap<>();
    final Map<TraceElement, List<TraceElement>> backward = new LinkedHashMap<>();
    while (leftIterator.hasNext() && rightIterator.hasNext()) {
      final TraceElement left = leftIterator.next();
      final TraceElement right = rightIterator.next();

      forward.put(left, Collections.singletonList(right));
      backward.put(right, Collections.singletonList(left));
    }

    while (leftIterator.hasNext()) {
      forward.put(leftIterator.next(), Collections.emptyList());
    }

    return Result.of(forward, backward);
  }
}
