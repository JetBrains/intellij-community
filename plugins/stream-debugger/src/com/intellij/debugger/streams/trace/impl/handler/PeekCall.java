// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.impl.handler;

import com.intellij.debugger.streams.trace.impl.handler.type.GenericType;
import com.intellij.debugger.streams.wrapper.CallArgument;
import com.intellij.debugger.streams.wrapper.IntermediateStreamCall;
import com.intellij.debugger.streams.wrapper.StreamCallType;
import com.intellij.debugger.streams.wrapper.impl.CallArgumentImpl;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.CommonClassNames;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public class PeekCall implements IntermediateStreamCall {
  private final List<CallArgument> myLambda;
  private final GenericType myElementType;

  public PeekCall(@NotNull String lambda, @NotNull GenericType elementType) {
    myLambda = Collections.singletonList(new CallArgumentImpl(CommonClassNames.JAVA_LANG_OBJECT, lambda));
    myElementType = elementType;
  }

  @NotNull
  @Override
  public String getName() {
    return "peek";
  }

  @NotNull
  @Override
  public List<CallArgument> getArguments() {
    return myLambda;
  }

  @NotNull
  @Override
  public TextRange getTextRange() {
    return TextRange.EMPTY_RANGE;
  }

  @NotNull
  @Override
  public StreamCallType getType() {
    return StreamCallType.INTERMEDIATE;
  }

  @NotNull
  @Override
  public GenericType getTypeAfter() {
    return myElementType;
  }

  @NotNull
  @Override
  public GenericType getTypeBefore() {
    return myElementType;
  }
}
