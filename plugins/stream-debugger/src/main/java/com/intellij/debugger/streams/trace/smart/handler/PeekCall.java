package com.intellij.debugger.streams.trace.smart.handler;

import com.intellij.debugger.streams.wrapper.MethodCall;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public class PeekCall implements MethodCall {
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
}
