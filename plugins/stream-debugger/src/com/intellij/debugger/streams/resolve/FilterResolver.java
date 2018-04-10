// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.resolve;

import com.intellij.debugger.streams.trace.TraceElement;
import com.intellij.debugger.streams.trace.TraceInfo;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Vitaliy.Bibaev
 */
public class FilterResolver implements ValuesOrderResolver {
  @NotNull
  @Override
  public Result resolve(@NotNull TraceInfo info) {
    final Map<Integer, TraceElement> before = info.getValuesOrderBefore();
    final Map<Integer, TraceElement> after = info.getValuesOrderAfter();
    assert before.size() >= after.size();
    final Map<TraceElement, List<TraceElement>> forward = new LinkedHashMap<>();
    final Map<TraceElement, List<TraceElement>> backward = new LinkedHashMap<>();

    final int[] beforeTimes = before.keySet().stream().mapToInt(Integer::intValue).sorted().toArray();
    final int[] afterTimes = after.keySet().stream().mapToInt(Integer::intValue).sorted().toArray();

    int beforeIndex = 0;
    for (final int afterTime : afterTimes) {
      final TraceElement afterElement = after.get(afterTime);
      final Value afterValue = afterElement.getValue();
      while (beforeIndex < beforeTimes.length) {
        final TraceElement beforeElement = before.get(beforeTimes[beforeIndex]);
        if (Objects.equals(beforeElement.getValue(), afterValue)) {
          forward.put(beforeElement, Collections.singletonList(afterElement));
          backward.put(afterElement, Collections.singletonList(beforeElement));
          beforeIndex++;
          break;
        }

        forward.put(beforeElement, Collections.emptyList());
        beforeIndex++;
      }
    }

    while (beforeIndex < beforeTimes.length) {
      final int beforeTime = beforeTimes[beforeIndex];
      forward.put(before.get(beforeTime), Collections.emptyList());
      beforeIndex++;
    }

    return Result.of(forward, backward);
  }
}
