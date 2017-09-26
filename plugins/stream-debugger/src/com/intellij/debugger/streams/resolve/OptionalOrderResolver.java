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

import com.intellij.debugger.streams.trace.TraceElement;
import com.intellij.debugger.streams.trace.TraceInfo;
import com.intellij.debugger.streams.wrapper.TraceUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Vitaliy.Bibaev
 */
public class OptionalOrderResolver implements ValuesOrderResolver {
  @NotNull
  @Override
  public Result resolve(@NotNull TraceInfo info) {
    final Map<TraceElement, List<TraceElement>> forward = new LinkedHashMap<>();
    final Map<TraceElement, List<TraceElement>> backward = new LinkedHashMap<>();

    final Map<Integer, TraceElement> before = info.getValuesOrderBefore();
    final Map<Integer, TraceElement> after = info.getValuesOrderAfter();

    if (after.isEmpty()) {
      before.values().forEach(x -> forward.put(x, Collections.emptyList()));
    }
    else {
      assert after.size() == 1;
      final TraceElement optionalContent = after.values().iterator().next();
      final List<TraceElement> backwardTrace = new ArrayList<>();
      final Object optionalKey = TraceUtil.extractKey(optionalContent);
      for (final TraceElement beforeElement : before.values()) {
        if (Objects.equals(TraceUtil.extractKey(beforeElement), optionalKey)) {
          backwardTrace.add(beforeElement);
          forward.put(beforeElement, Collections.singletonList(optionalContent));
        }
        else {
          forward.put(beforeElement, Collections.emptyList());
        }
      }

      backward.put(optionalContent, backwardTrace);
    }

    return Result.of(forward, backward);
  }
}
