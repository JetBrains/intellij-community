// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.resolve;

import com.intellij.debugger.streams.trace.IntermediateState;
import com.intellij.debugger.streams.trace.NextAwareState;
import com.intellij.debugger.streams.trace.PrevAwareState;
import com.intellij.debugger.streams.wrapper.IntermediateStreamCall;
import com.intellij.debugger.streams.wrapper.StreamCall;
import com.intellij.debugger.streams.wrapper.TerminatorStreamCall;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vitaliy.Bibaev
 */
public interface ResolvedStreamCall<CALL extends StreamCall, STATE_BEFORE extends IntermediateState, STATE_AFTER extends IntermediateState> {
  @NotNull
  CALL getCall();

  @Nullable
  STATE_BEFORE getStateBefore();

  @Nullable
  STATE_AFTER getStateAfter();

  interface Intermediate extends ResolvedStreamCall<IntermediateStreamCall, NextAwareState, PrevAwareState> {
    @NotNull
    @Override
    NextAwareState getStateBefore();

    @NotNull
    @Override
    PrevAwareState getStateAfter();
  }

  interface Terminator extends ResolvedStreamCall<TerminatorStreamCall, NextAwareState, PrevAwareState> {
    @NotNull
    @Override
    NextAwareState getStateBefore();
  }
}
