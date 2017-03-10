package com.intellij.debugger.streams.trace.smart.handler;

import com.intellij.debugger.streams.wrapper.StreamCall;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public class TerminatorHandler extends HandlerBase {
  private final PeekTracerHandler myPeekTracerHandler = new PeekTracerHandler(Integer.MAX_VALUE, "terminator");

  @NotNull
  @Override
  protected List<Variable> getVariables() {
    return myPeekTracerHandler.getVariables();
  }

  @NotNull
  @Override
  public List<StreamCall> additionalCallsBefore() {
    return myPeekTracerHandler.additionalCallsBefore();
  }

  @NotNull
  @Override
  public List<StreamCall> additionalCallsAfter() {
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public String prepareResult() {
    return myPeekTracerHandler.prepareResult();
  }

  @NotNull
  @Override
  public String getResultExpression() {
    return myPeekTracerHandler.getResultExpression();
  }
}
