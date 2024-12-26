// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.resolve.impl;

import com.intellij.debugger.streams.resolve.ResolvedStreamCall;
import com.intellij.debugger.streams.trace.NextAwareState;
import com.intellij.debugger.streams.trace.PrevAwareState;
import com.intellij.debugger.streams.wrapper.TerminatorStreamCall;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vitaliy.Bibaev
 */
public class ResolvedTerminatorCallImpl implements ResolvedStreamCall.Terminator {
  private final TerminatorStreamCall myCall;
  private final NextAwareState myStateBefore;
  private final PrevAwareState myStateAfter;

  public ResolvedTerminatorCallImpl(@NotNull TerminatorStreamCall call,
                                    @NotNull NextAwareState stateBefore,
                                    @NotNull PrevAwareState stateAfter) {
    myCall = call;
    myStateBefore = stateBefore;
    myStateAfter = stateAfter;
  }

  @Override
  public @NotNull TerminatorStreamCall getCall() {
    return myCall;
  }

  @Override
  public @Nullable PrevAwareState getStateAfter() {
    return myStateAfter;
  }

  @Override
  public @NotNull NextAwareState getStateBefore() {
    return myStateBefore;
  }
}
