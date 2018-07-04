// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.impl.interpret;

import com.intellij.debugger.streams.trace.TraceElement;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * anyMatch(condition) -> filter(condition).anyMatch(x -> true);   (result is true <~> any element passed thought filter)
 *
 * @author Vitaliy.Bibaev
 */
public class AnyMatchTraceInterpreter extends MatchInterpreterBase {
  @Override
  protected boolean getResult(@NotNull Collection<TraceElement> traceBeforeFilter, @NotNull Collection<TraceElement> traceAfterFilter) {
    return !traceAfterFilter.isEmpty();
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
