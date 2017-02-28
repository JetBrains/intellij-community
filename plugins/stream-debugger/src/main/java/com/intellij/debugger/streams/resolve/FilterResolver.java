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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Vitaliy.Bibaev
 */
public class FilterResolver implements ValuesOrderResolver {
  @NotNull
  @Override
  public Pair<Map<TraceElement, List<TraceElement>>, Map<TraceElement, List<TraceElement>>> resolve(@NotNull Map<Integer, TraceElement> previousCalls,
                                                                                                    @NotNull Map<Integer, TraceElement> nextCalls) {

    assert previousCalls.size() >= nextCalls.size();
    final Map<TraceElement, List<TraceElement>> forward = new LinkedHashMap<>();
    final Map<TraceElement, List<TraceElement>> backward = new LinkedHashMap<>();

    // TODO: O(n) solution
    //final Integer[] beforeTimes = new Integer[previousCalls.size()];
    //final Integer[] afterTimes = new Integer[previousCalls.size()];
    //previousCalls.keySet().toArray(beforeTimes);
    //nextCalls.keySet().toArray(afterTimes);
    //
    //int rightIndex = 0;
    //for (int i = 0; i < beforeTimes.length; i++) {
    //  final int leftTime = beforeTimes[i];
    //  final int nextLeftTime = i + 1 < beforeTimes.length ? beforeTimes[i + 1] : Integer.MAX_VALUE;
    //  while (rightIndex < afterTimes.length && afterTimes[rightIndex] < nextLeftTime) {
    //
    //  }
    //}

    // this is O(n^2) solution
    for (TraceElement leftValue : previousCalls.values()) {
      if (nextCalls.containsValue(leftValue)) {
        forward.put(leftValue, Collections.singletonList(leftValue));
        backward.put(leftValue, Collections.singletonList(leftValue));
      }
      else {
        forward.put(leftValue, Collections.emptyList());
      }
    }

    return Pair.create(forward, backward);
  }
}
