// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace;

import com.intellij.debugger.streams.wrapper.StreamCall;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public interface NextAwareState extends IntermediateState {
  @NotNull
  StreamCall getNextCall();

  @NotNull
  List<TraceElement> getNextValues(@NotNull TraceElement value);
}
