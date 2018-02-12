// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.resolve.impl;

import com.intellij.debugger.streams.resolve.ResolvedStreamCall;
import com.intellij.debugger.streams.trace.NextAwareState;
import com.intellij.debugger.streams.trace.PrevAwareState;
import com.intellij.debugger.streams.wrapper.IntermediateStreamCall;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public class ResolvedIntermediateCallImpl implements ResolvedStreamCall.Intermediate {
  private final IntermediateStreamCall myCall;
  private final NextAwareState myStateBefore;
  private final PrevAwareState myStateAfter;

  public ResolvedIntermediateCallImpl(@NotNull IntermediateStreamCall call,
                                      @NotNull NextAwareState stateBefore,
                                      @NotNull PrevAwareState stateAfter) {
    myCall = call;
    myStateBefore = stateBefore;
    myStateAfter = stateAfter;
  }

  @NotNull
  @Override
  public IntermediateStreamCall getCall() {
    return myCall;
  }

  @NotNull
  @Override
  public NextAwareState getStateBefore() {
    return myStateBefore;
  }

  @NotNull
  @Override
  public PrevAwareState getStateAfter() {
    return myStateAfter;
  }
}
