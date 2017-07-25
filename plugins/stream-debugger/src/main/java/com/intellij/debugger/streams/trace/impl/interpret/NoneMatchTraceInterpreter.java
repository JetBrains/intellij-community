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
package com.intellij.debugger.streams.trace.impl.interpret;

import com.intellij.debugger.streams.trace.TraceElement;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * noneMatch(condition) -> filter(condition).noneMatch(x -> true); (result is true <~> no elements which passed thought filter)
 *
 * @author Vitaliy.Bibaev
 */
public class NoneMatchTraceInterpreter extends MatchInterpreterBase {
  @Override
  protected boolean getResult(@NotNull Collection<TraceElement> traceBeforeFilter, @NotNull Collection<TraceElement> traceAfterFilter) {
    return traceAfterFilter.isEmpty();
  }

  @NotNull
  @Override
  protected Action getAction(boolean result) {
    if (result) {
      return Action.CONNECT_DIFFERENCE;
    }
    else {
      return Action.CONNECT_FILTERED;
    }
  }
}
