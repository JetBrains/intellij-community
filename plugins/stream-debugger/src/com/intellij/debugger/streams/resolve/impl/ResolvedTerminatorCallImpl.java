// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @NotNull
  @Override
  public TerminatorStreamCall getCall() {
    return myCall;
  }

  @Nullable
  @Override
  public PrevAwareState getStateAfter() {
    return myStateAfter;
  }

  @NotNull
  @Override
  public NextAwareState getStateBefore() {
    return myStateBefore;
  }
}
