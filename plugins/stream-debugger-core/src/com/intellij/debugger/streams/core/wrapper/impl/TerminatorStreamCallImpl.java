// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.core.wrapper.impl;

import com.intellij.debugger.streams.core.trace.impl.handler.type.GenericType;
import com.intellij.debugger.streams.core.wrapper.CallArgument;
import com.intellij.debugger.streams.core.wrapper.StreamCallType;
import com.intellij.debugger.streams.core.wrapper.TerminatorStreamCall;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public class TerminatorStreamCallImpl extends StreamCallImpl implements TerminatorStreamCall {
  private final GenericType myTypeBefore;
  private final GenericType myReturnType;
  private final Boolean myReturnsVoid;

  public TerminatorStreamCallImpl(@NotNull String name,
                                  @NotNull String genericArgs,
                                  @NotNull List<CallArgument> args,
                                  @NotNull GenericType typeBefore,
                                  @NotNull GenericType resultType,
                                  @NotNull TextRange range,
                                  @NotNull Boolean returnsVoid
                                  ) {
    super(name, genericArgs, args, StreamCallType.TERMINATOR, range);
    myTypeBefore = typeBefore;
    myReturnType = resultType;
    myReturnsVoid = returnsVoid;
  }

  @Override
  public @NotNull GenericType getTypeBefore() {
    return myTypeBefore;
  }

  @Override
  public @NotNull GenericType getResultType() {
    return myReturnType;
  }

  @Override
  public Boolean returnsVoid() {
    return myReturnsVoid;
  }
}
