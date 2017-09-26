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
import com.intellij.debugger.streams.resolve.ResolvedStreamChain;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public class ResolvedStreamChainImpl implements ResolvedStreamChain {

  private final ResolvedStreamCall.Terminator myTerminator;
  private final List<ResolvedStreamCall.Intermediate> myIntermediateCalls;

  ResolvedStreamChainImpl(@NotNull ResolvedStreamCall.Terminator terminator,
                          @NotNull List<ResolvedStreamCall.Intermediate> intermediates) {
    myTerminator = terminator;
    myIntermediateCalls = Collections.unmodifiableList(new ArrayList<>(intermediates));
  }

  @NotNull
  @Override
  public ResolvedStreamCall.Terminator getTerminator() {
    return myTerminator;
  }

  @NotNull
  @Override
  public List<ResolvedStreamCall.Intermediate> getIntermediateCalls() {
    return myIntermediateCalls;
  }

  public static class Builder {
    private List<ResolvedStreamCall.Intermediate> myIntermediates = new ArrayList<>();
    private ResolvedStreamCall.Terminator myTerminator;

    public void addIntermediate(@NotNull ResolvedStreamCall.Intermediate intermediate) {
      myIntermediates.add(intermediate);
    }

    public void setTerminator(@NotNull ResolvedStreamCall.Terminator terminator) {
      myTerminator = terminator;
    }

    public ResolvedStreamChain build() {
      if (myTerminator == null) {
        throw new IllegalStateException("terminator not specified");
      }

      return new ResolvedStreamChainImpl(myTerminator, myIntermediates);
    }
  }
}
