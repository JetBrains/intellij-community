// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.impl.interpret;

import com.intellij.debugger.streams.trace.TraceElement;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * allMatch(condition) -> filter(!condition).noneMatch(x -> true); (result is true <~> no elements which passed thought filter)
 *
 * @author Vitaliy.Bibaev
 */
public class AllMatchTraceInterpreter extends MatchInterpreterBase {
  @Override
  protected boolean getResult(@NotNull Collection<TraceElement> traceBeforeFilter, @NotNull Collection<TraceElement> traceAfterFilter) {
    return traceBeforeFilter.size() == traceAfterFilter.size();
  }

  @NotNull
  @Override
  protected Action getAction(boolean result) {
    if (result) {
      return Action.CONNECT_FILTERED;
    }
    else {
      return Action.CONNECT_DIFFERENCE;
    }
  }
}
