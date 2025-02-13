// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.core.resolve.impl;

import com.intellij.debugger.streams.core.resolve.ResolvedStreamCall;
import com.intellij.debugger.streams.core.trace.NextAwareState;
import com.intellij.debugger.streams.core.trace.PrevAwareState;
import com.intellij.debugger.streams.core.wrapper.IntermediateStreamCall;
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

  @Override
  public @NotNull IntermediateStreamCall getCall() {
    return myCall;
  }

  @Override
  public @NotNull NextAwareState getStateBefore() {
    return myStateBefore;
  }

  @Override
  public @NotNull PrevAwareState getStateAfter() {
    return myStateAfter;
  }
}
