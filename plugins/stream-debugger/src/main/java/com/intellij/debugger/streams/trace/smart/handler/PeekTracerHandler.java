package com.intellij.debugger.streams.trace.smart.handler;

import com.intellij.debugger.streams.wrapper.MethodCall;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public class PeekTracerHandler extends HandlerBase {
  private final List<Variable> myVariables = new ArrayList<>();

  public PeekTracerHandler(int num, @NotNull String name) {
  }

  @NotNull
  @Override
  public List<MethodCall> additionalCallsBefore() {
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public List<MethodCall> additionalCallsAfter() {
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public String prepareResult() {
    return "";
  }

  @NotNull
  @Override
  public String getResultExpression() {
    return "";
  }

  @NotNull
  @Override
  protected List<Variable> getVariables() {
    return myVariables;
  }
}
