// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.impl;

import com.intellij.debugger.streams.trace.IntermediateState;
import com.intellij.debugger.streams.trace.TraceElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
abstract class StateBase implements IntermediateState {
  private final List<TraceElement> myElements;

  StateBase(@NotNull List<TraceElement> elements) {
    myElements = List.copyOf(elements);
  }

  @Override
  public @NotNull List<TraceElement> getTrace() {
    return myElements;
  }
}
