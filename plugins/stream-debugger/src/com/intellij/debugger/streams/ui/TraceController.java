// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.ui;

import com.intellij.debugger.streams.trace.TraceElement;
import com.intellij.debugger.streams.wrapper.StreamCall;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public interface TraceController extends ValuesHighlightingListener {
  @NotNull
  List<Value> getValues();

  @NotNull
  List<TraceElement> getTrace();

  @Nullable
  StreamCall getNextCall();

  @Nullable
  StreamCall getPrevCall();

  @NotNull
  List<TraceElement> getNextValues(@NotNull TraceElement element);

  @NotNull
  List<TraceElement> getPrevValues(@NotNull TraceElement element);

  default boolean isSelectionExists() {
    return isSelectionExists(PropagationDirection.BACKWARD) || isSelectionExists(PropagationDirection.FORWARD);
  }

  boolean isSelectionExists(@NotNull PropagationDirection direction);

  void register(@NotNull TraceContainer listener);
}
