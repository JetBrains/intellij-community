// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.resolve;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public interface ResolvedStreamChain {

  @NotNull
  ResolvedStreamCall.Terminator getTerminator();

  @NotNull
  List<ResolvedStreamCall.Intermediate> getIntermediateCalls();

  default int length() {
    return getIntermediateCalls().size() + 2;
  }
}
