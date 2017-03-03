package com.intellij.debugger.streams.trace.smart.handler;

import com.intellij.debugger.streams.wrapper.StreamCall;
import com.intellij.debugger.streams.wrapper.StreamCallType;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public class PeekCall implements StreamCall {
  private final String myLambda;

  public PeekCall(@NotNull String lambda) {
    myLambda = lambda;
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
}
