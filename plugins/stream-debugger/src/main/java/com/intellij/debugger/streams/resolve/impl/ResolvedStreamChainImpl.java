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

  private final ResolvedStreamCall.Producer myProducer;
  private final ResolvedStreamCall.Terminator myTerminator;
  private final List<ResolvedStreamCall.Intermediate> myIntermediateCalls;

  public ResolvedStreamChainImpl(@NotNull ResolvedStreamCall.Producer producer,
                                 @NotNull ResolvedStreamCall.Terminator terminator,
                                 @NotNull List<ResolvedStreamCall.Intermediate> intermediates) {
    myProducer = producer;
    myTerminator = terminator;
    myIntermediateCalls = Collections.unmodifiableList(new ArrayList<>(intermediates));
  }

  @NotNull
  @Override
  public ResolvedStreamCall.Producer getProducer() {
    return myProducer;
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
    private ResolvedStreamCall.Producer myProducer;
    private ResolvedStreamCall.Terminator myTerminator;
    private List<ResolvedStreamCall.Intermediate> myIntermediates = new ArrayList<>();

    public Builder setProducer(@NotNull ResolvedStreamCall.Producer producer) {
      myProducer = producer;
      return this;
    }

    public Builder addIntermediate(@NotNull ResolvedStreamCall.Intermediate intermediate) {
      myIntermediates.add(intermediate);
      return this;
    }

    public Builder setTerminator(@NotNull ResolvedStreamCall.Terminator terminator) {
      myTerminator = terminator;
      return this;
    }

    public ResolvedStreamChain build() {
      if (myProducer == null) {
        throw new IllegalStateException("producer not specified");
      }

      if (myTerminator == null) {
        throw new IllegalStateException("terminator not specified");
      }

      return new ResolvedStreamChainImpl(myProducer, myTerminator, myIntermediates);
    }
  }
}
