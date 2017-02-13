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

import com.intellij.openapi.util.Pair;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Warning: now distinct works according 'same' relation. Not Stream API provide distinct according 'equals'
 *
 * @author Vitaliy.Bibaev
 */
public class DistinctResolver implements ValuesOrderResolver {

  @NotNull
  @Override
  public Pair<Map<Value, List<Value>>, Map<Value, List<Value>>> resolve(@NotNull Map<Integer, Value> previousCalls,
                                                                        @NotNull Map<Integer, Value> nextCalls) {
    assert previousCalls.size() >= nextCalls.size();

    final List<Value> distinct = previousCalls.values().stream().distinct().collect(Collectors.toList());
    final Map<Value, List<Value>> forward = new LinkedHashMap<>();
    final Map<Value, List<Value>> backward = new LinkedHashMap<>();

    for (Value previous : previousCalls.values()) {
      forward.computeIfAbsent(previous, Collections::singletonList);
    }

    return Pair.create(forward, backward);
  }
}
