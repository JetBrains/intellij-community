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
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Vitaliy.Bibaev
 */
public class ResolvedTraceImpl implements ResolvedTrace {
  private final List<TraceElement> myValues;
  private final Map<TraceElement, List<TraceElement>> myPrevious;
  private final Map<TraceElement, List<TraceElement>> myNext;

  public ResolvedTraceImpl(@NotNull List<TraceElement> values,
                           @NotNull Map<TraceElement, List<TraceElement>> toPrev,
                           @NotNull Map<TraceElement, List<TraceElement>> toNext) {
    myValues = values.stream().sorted().collect(Collectors.toList());
    myPrevious = toPrev;
    myNext = toNext;
  }

  @NotNull
  @Override
  public List<TraceElement> getPreviousValues(@NotNull TraceElement value) {
    return extractList(myPrevious, value);
  }

  @NotNull
  @Override
  public List<TraceElement> getNextValues(@NotNull TraceElement value) {
    return extractList(myNext, value);
  }

  @NotNull
  @Override
  public List<TraceElement> getValues() {
    return Collections.unmodifiableList(myValues);
  }

  @NotNull
  private static List<TraceElement> extractList(@NotNull Map<TraceElement, List<TraceElement>> values, @NotNull TraceElement key) {
    final List<TraceElement> result = values.get(key);
    return result == null ? Collections.emptyList() : Collections.unmodifiableList(result);
  }
}
