// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.core.trace.impl;

import com.intellij.debugger.streams.core.trace.IntermediateState;
import com.intellij.debugger.streams.core.trace.TraceElement;
import com.intellij.debugger.streams.core.trace.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  @Override
  public @Nullable Value getStreamResult() {
    return null;
  }
}
