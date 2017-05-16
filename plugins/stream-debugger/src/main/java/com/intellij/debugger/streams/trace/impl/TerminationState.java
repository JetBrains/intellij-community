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
package com.intellij.debugger.streams.trace.impl;

import com.intellij.debugger.streams.trace.PrevAwareState;
import com.intellij.debugger.streams.trace.TraceElement;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Vitaliy.Bibaev
 */
public class TerminationState extends StateBase implements PrevAwareState {
  private final Value myResult;
  @NotNull private final Map<TraceElement, List<TraceElement>> myToPrev;

  public TerminationState(@NotNull Value result,
                          @NotNull List<TraceElement> elements,
                          @NotNull Map<TraceElement, List<TraceElement>> toPrevMapping) {
    super(elements);
    myResult = result;
    myToPrev = toPrevMapping;
  }

  @NotNull
  @Override
  public Collection<Value> getRawValues() {
    return Collections.singleton(myResult);
  }

  @NotNull
  @Override
  public List<TraceElement> getPrevValues(@NotNull TraceElement value) {
    return myToPrev.getOrDefault(value, Collections.emptyList());
  }
}
