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
import com.intellij.debugger.streams.wrapper.ProducerStreamCall;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vitaliy.Bibaev
 */
public class ResolvedProducerCallImpl implements ResolvedStreamCall.Producer {

  private final ProducerStreamCall myCall;
  private final IntermediateState myStateAfter;

  public ResolvedProducerCallImpl(@NotNull ProducerStreamCall call, @NotNull IntermediateState stateAfter) {
    myCall = call;
    myStateAfter = stateAfter;
  }

  @NotNull
  @Override
  public ProducerStreamCall getCall() {
    return myCall;
  }

  @NotNull
  @Override
  public IntermediateState getStateAfter() {
    return myStateAfter;
  }
}
