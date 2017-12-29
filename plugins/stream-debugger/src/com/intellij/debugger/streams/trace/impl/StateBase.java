// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.impl;

import com.intellij.debugger.streams.trace.IntermediateState;
import com.intellij.debugger.streams.trace.TraceElement;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
abstract class StateBase implements IntermediateState {
  private final List<TraceElement> myElements;

  StateBase(@NotNull List<TraceElement> elements) {
    myElements = Collections.unmodifiableList(new ArrayList<>(elements));
  }

  @NotNull
  @Override
  public List<TraceElement> getTrace() {
    return myElements;
  }
}
