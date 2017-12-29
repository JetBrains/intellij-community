// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
