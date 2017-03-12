/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.debugger.streams.resolve;

import com.intellij.debugger.streams.trace.smart.TraceElement;
import com.intellij.debugger.streams.trace.smart.resolve.TraceInfo;
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
      while (true) {
        final TraceElement beforeElement = before.get(beforeTimes[beforeIndex]);
        if (beforeElement.getValue().equals(afterValue)) {
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
