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
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Vitaliy.Bibaev
 */
public class FlatMapResolver implements ValuesOrderResolver {
  @NotNull
  @Override
  public Result resolve(@NotNull TraceInfo info) {
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
