// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.wrapper.impl;

import com.intellij.debugger.streams.trace.impl.handler.type.GenericType;
import com.intellij.debugger.streams.wrapper.CallArgument;
import com.intellij.debugger.streams.wrapper.IntermediateStreamCall;
import com.intellij.debugger.streams.wrapper.StreamCallType;
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
                                    @NotNull List<CallArgument> args,
                                    @NotNull GenericType typeBefore,
                                    @NotNull GenericType typeAfter,
                                    @NotNull TextRange range) {
    super(name, args, StreamCallType.INTERMEDIATE, range);
    myTypeBefore = typeBefore;
    myTypeAfter = typeAfter;
  }

  @NotNull
  @Override
  public GenericType getTypeBefore() {
    return myTypeBefore;
  }

  @NotNull
  @Override
  public GenericType getTypeAfter() {
    return myTypeAfter;
  }
}
