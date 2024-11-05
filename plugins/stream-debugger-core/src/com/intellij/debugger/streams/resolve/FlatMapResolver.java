// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.resolve;

import com.intellij.debugger.streams.trace.TraceElement;
import com.intellij.debugger.streams.trace.TraceInfo;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Vitaliy.Bibaev
 */
public class FlatMapResolver implements ValuesOrderResolver {
  @Override
  public @NotNull Result resolve(@NotNull TraceInfo info) {
    final Map<Integer, TraceElement> before = info.getValuesOrderBefore();
    final Map<Integer, TraceElement> after = info.getValuesOrderAfter();
    final Map<TraceElement, List<TraceElement>> forward = new HashMap<>();
    final Map<TraceElement, List<TraceElement>> backward = new HashMap<>();

    final int[] beforeTimes = before.keySet().stream().mapToInt(Integer::intValue).toArray();
    final int[] afterTimes = after.keySet().stream().mapToInt(Integer::intValue).toArray();

    int beforeIndex = 0;
    for (int i = 0; i < beforeTimes.length; i++) {
      final TraceElement afterElement = before.get(beforeTimes[i]);
      final List<TraceElement> afterElements = new ArrayList<>();
      final int nextBeforeTime = i + 1 < beforeTimes.length ? beforeTimes[i + 1] : Integer.MAX_VALUE;
      while (beforeIndex < afterTimes.length && afterTimes[beforeIndex] < nextBeforeTime) {
        final TraceElement beforeElement = after.get(afterTimes[beforeIndex]);

        afterElements.add(beforeElement);
        backward.put(beforeElement, Collections.singletonList(afterElement));
        beforeIndex++;
      }

      forward.put(afterElement, afterElements);
    }

    return Result.of(forward, backward);
  }
}
