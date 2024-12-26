// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.wrapper.impl;

import com.intellij.debugger.streams.wrapper.CallArgument;
import com.intellij.debugger.streams.wrapper.StreamCall;
import com.intellij.debugger.streams.wrapper.StreamCallType;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public abstract class StreamCallImpl implements StreamCall {
  private final String myName;
  private final List<CallArgument> myArgs;
  private final StreamCallType myType;
  private final TextRange myTextRange;

  StreamCallImpl(@NotNull String name,
                 @NotNull List<CallArgument> args,
                 @NotNull StreamCallType type,
                 @NotNull TextRange range) {
    myName = name;
    myArgs = args;
    myType = type;
    myTextRange = range;
  }

  @Override
  public @NotNull TextRange getTextRange() {
    return myTextRange;
  }

  @Override
  public @NotNull @NlsSafe String getName() {
    return myName;
  }

  @Override
  public @NotNull List<CallArgument> getArguments() {
    return myArgs;
  }

  @Override
  public @NotNull StreamCallType getType() {
    return myType;
  }
}
