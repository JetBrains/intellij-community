// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.core.ui;

import com.intellij.debugger.streams.core.trace.TraceElement;
import com.intellij.debugger.streams.core.trace.Value;
import com.intellij.debugger.streams.core.wrapper.StreamCall;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public interface TraceController extends ValuesHighlightingListener {
  @Nullable
  Value getStreamResult();

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
