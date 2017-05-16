/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.debugger.streams.resolve.impl;

import com.intellij.debugger.streams.resolve.ResolvedStreamCall;
import com.intellij.debugger.streams.trace.IntermediateState;
import com.intellij.debugger.streams.wrapper.TerminatorStreamCall;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vitaliy.Bibaev
 */
public class ResolvedTerminatorCallImpl implements ResolvedStreamCall.Terminator {
  private final TerminatorStreamCall myCall;
  private final IntermediateState myStateBefore;
  private final IntermediateState myStateAfter;

  public ResolvedTerminatorCallImpl(@NotNull TerminatorStreamCall call,
                                    @NotNull IntermediateState stateBefore,
                                    @NotNull IntermediateState stateAfter) {
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
  public IntermediateState getStateAfter() {
    return myStateAfter;
  }

  @NotNull
  @Override
  public IntermediateState getStateBefore() {
    return myStateBefore;
  }
}
