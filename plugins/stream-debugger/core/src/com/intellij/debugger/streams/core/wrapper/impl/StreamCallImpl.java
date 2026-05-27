// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.core.wrapper.impl;

import com.intellij.debugger.streams.core.wrapper.CallArgument;
import com.intellij.debugger.streams.core.wrapper.StreamCall;
import com.intellij.debugger.streams.core.wrapper.StreamCallType;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public abstract class StreamCallImpl implements StreamCall {
  private final String myName;
  private final String myGenericArgs;
  private final List<CallArgument> myArgs;
  private final StreamCallType myType;
  private final TextRange myTextRange;

  StreamCallImpl(@NotNull String name,
                 @NotNull String genericArgs,
                 @NotNull List<CallArgument> args,
                 @NotNull StreamCallType type,
                 @NotNull TextRange range) {
    myName = name;
    myGenericArgs = genericArgs;
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
  public @NotNull String getGenericArguments() {
    return myGenericArgs;
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
