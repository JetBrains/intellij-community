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
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Vitaliy.Bibaev
 */
public class FlatMapResolver implements ValuesOrderResolver {
  @NotNull
  @Override
  public Pair<Map<TraceElement, List<TraceElement>>, Map<TraceElement, List<TraceElement>>> resolve(@NotNull Map<Integer, TraceElement> previousCalls,
                                                                                                    @NotNull Map<Integer, TraceElement> nextCalls) {
    final Map<TraceElement, List<TraceElement>> forward = new LinkedHashMap<>();
    final Map<TraceElement, List<TraceElement>> backward = new LinkedHashMap<>();

    nextCalls.values().stream().distinct().forEach(x -> backward.put(x, new ArrayList<>()));

    final Integer[] beforeTimes = new Integer[previousCalls.size()];
    final Integer[] afterTimes = new Integer[nextCalls.size()];
    previousCalls.keySet().toArray(beforeTimes);
    nextCalls.keySet().toArray(afterTimes);

    int rightIndex = 0;
    for (int i = 0; i < beforeTimes.length; i++) {
      final TraceElement leftValue = previousCalls.get(beforeTimes[i]);
      final List<TraceElement> right = new ArrayList<>();
      final int nextLeftTime = i + 1 < beforeTimes.length ? beforeTimes[i + 1] : Integer.MAX_VALUE;
      while (rightIndex < afterTimes.length && afterTimes[rightIndex] < nextLeftTime) {
        final TraceElement rightValue = nextCalls.get(afterTimes[rightIndex]);

        right.add(rightValue);
        backward.get(rightValue).add(leftValue);
        rightIndex++;
      }

      forward.put(leftValue, right);
    }

    return Pair.create(forward, backward);
  }
}
