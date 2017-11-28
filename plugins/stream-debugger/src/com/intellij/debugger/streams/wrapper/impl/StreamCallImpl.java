// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.wrapper.impl;

import com.intellij.debugger.streams.wrapper.CallArgument;
import com.intellij.debugger.streams.wrapper.StreamCall;
import com.intellij.debugger.streams.wrapper.StreamCallType;
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

  @NotNull
  @Override
  public TextRange getTextRange() {
    return myTextRange;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @NotNull
  @Override
  public List<CallArgument> getArguments() {
    return myArgs;
  }

  @NotNull
  @Override
  public StreamCallType getType() {
    return myType;
  }
}
