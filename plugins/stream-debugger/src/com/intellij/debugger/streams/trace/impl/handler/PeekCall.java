// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.impl.handler;

import com.intellij.debugger.streams.core.trace.impl.handler.type.GenericType;
import com.intellij.debugger.streams.core.wrapper.CallArgument;
import com.intellij.debugger.streams.core.wrapper.IntermediateStreamCall;
import com.intellij.debugger.streams.core.wrapper.StreamCallType;
import com.intellij.debugger.streams.core.wrapper.impl.CallArgumentImpl;
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

  @Override
  public @NotNull String getName() {
    return "peek";
  }

  @Override
  public @NotNull String getGenericArguments() {
    return "";
  }

  @Override
  public @NotNull List<CallArgument> getArguments() {
    return myLambda;
  }

  @Override
  public @NotNull TextRange getTextRange() {
    return TextRange.EMPTY_RANGE;
  }

  @Override
  public @NotNull StreamCallType getType() {
    return StreamCallType.INTERMEDIATE;
  }

  @Override
  public @NotNull GenericType getTypeAfter() {
    return myElementType;
  }

  @Override
  public @NotNull GenericType getTypeBefore() {
    return myElementType;
  }
}
