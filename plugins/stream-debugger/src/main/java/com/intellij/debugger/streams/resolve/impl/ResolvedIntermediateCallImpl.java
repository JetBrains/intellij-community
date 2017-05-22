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
import com.intellij.debugger.streams.trace.BidirectionalAwareState;
import com.intellij.debugger.streams.wrapper.IntermediateStreamCall;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public class ResolvedIntermediateCallImpl implements ResolvedStreamCall.Intermediate {
  private final IntermediateStreamCall myCall;
  private final BidirectionalAwareState myStateBefore;
  private final BidirectionalAwareState myStateAfter;

  public ResolvedIntermediateCallImpl(@NotNull IntermediateStreamCall call,
                                      @NotNull BidirectionalAwareState stateBefore,
                                      @NotNull BidirectionalAwareState stateAfter) {
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
  public BidirectionalAwareState getStateAfter() {
    return myStateAfter;
  }

  @NotNull
  @Override
  public BidirectionalAwareState getStateBefore() {
    return myStateBefore;
  }
}
