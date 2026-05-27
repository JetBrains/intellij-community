// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.core.wrapper.impl;

import com.intellij.debugger.streams.core.trace.impl.handler.type.GenericType;
import com.intellij.debugger.streams.core.wrapper.CallArgument;
import com.intellij.debugger.streams.core.wrapper.IntermediateStreamCall;
import com.intellij.debugger.streams.core.wrapper.StreamCallType;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public class IntermediateStreamCallImpl extends StreamCallImpl implements IntermediateStreamCall {

  private final GenericType myTypeBefore;
  private final GenericType myTypeAfter;

  public IntermediateStreamCallImpl(@NotNull String name,
                                    @NotNull String genericArgs,
                                    @NotNull List<CallArgument> args,
                                    @NotNull GenericType typeBefore,
                                    @NotNull GenericType typeAfter,
                                    @NotNull TextRange range) {
    super(name, genericArgs, args, StreamCallType.INTERMEDIATE, range);
    myTypeBefore = typeBefore;
    myTypeAfter = typeAfter;
  }

  @Override
  public @NotNull GenericType getTypeBefore() {
    return myTypeBefore;
  }

  @Override
  public @NotNull GenericType getTypeAfter() {
    return myTypeAfter;
  }
}
