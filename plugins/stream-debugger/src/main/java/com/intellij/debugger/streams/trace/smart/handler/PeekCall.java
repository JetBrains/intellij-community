package com.intellij.debugger.streams.trace.smart.handler;

import com.intellij.debugger.streams.trace.smart.handler.type.GenericType;
import com.intellij.debugger.streams.wrapper.IntermediateStreamCall;
import com.intellij.debugger.streams.wrapper.StreamCallType;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public class PeekCall implements IntermediateStreamCall {
  private final String myLambda;
  private final GenericType myElementType;

  public PeekCall(@NotNull String lambda, @NotNull GenericType elementType) {
    myLambda = lambda;
    myElementType = elementType;
  }

  @NotNull
  @Override
  public String getName() {
    return "peek";
  }

  @NotNull
  @Override
  public String getArguments() {
    return String.format("(%s)", myLambda);
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
