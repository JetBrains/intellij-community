package com.intellij.debugger.streams.trace.smart.handler;

import com.intellij.debugger.streams.wrapper.MethodCall;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public class PeekTracerHandler extends HandlerBase {
  public PeekTracerHandler(int callNumber, @NotNull String callName) {
    super(Arrays.asList());
  }

  @NotNull
  @Override
  public List<MethodCall> additionalCallsBefore() {
    return null;
  }

  @NotNull
  @Override
  public List<MethodCall> additionalCallsAfter() {
    return null;
  }

  @NotNull
  @Override
  public String prepareResult() {
    return null;
  }

  @NotNull
  @Override
  public String getResultExpression() {
    return null;
  }
}
