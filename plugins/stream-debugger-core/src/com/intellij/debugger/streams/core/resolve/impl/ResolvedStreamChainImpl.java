// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.core.resolve.impl;

import com.intellij.debugger.streams.core.resolve.ResolvedStreamCall;
import com.intellij.debugger.streams.core.resolve.ResolvedStreamChain;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
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
    myIntermediateCalls = List.copyOf(intermediates);
  }

  @Override
  public @NotNull ResolvedStreamCall.Terminator getTerminator() {
    return myTerminator;
  }

  @Override
  public @NotNull List<ResolvedStreamCall.Intermediate> getIntermediateCalls() {
    return myIntermediateCalls;
  }

  public static class Builder {
    private final List<ResolvedStreamCall.Intermediate> myIntermediates = new ArrayList<>();
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
