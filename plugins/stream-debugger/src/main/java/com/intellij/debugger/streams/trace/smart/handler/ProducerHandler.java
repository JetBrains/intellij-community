package com.intellij.debugger.streams.trace.smart.handler;

import com.intellij.debugger.streams.wrapper.StreamCall;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public class ProducerHandler extends HandlerBase {
  private final PeekTracerHandler myPeekTracerHandler = new PeekTracerHandler(0, "producer");

  @NotNull
  @Override
  protected List<Variable> getVariables() {
    return myPeekTracerHandler.getVariables();
  }

  @NotNull
  @Override
  public List<StreamCall> additionalCallsBefore() {
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public List<StreamCall> additionalCallsAfter() {
    return myPeekTracerHandler.additionalCallsAfter();
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
