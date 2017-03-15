package com.intellij.debugger.streams.trace.smart.handler;

import com.intellij.debugger.streams.trace.smart.handler.type.GenericType;
import com.intellij.debugger.streams.wrapper.IntermediateStreamCall;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public class ProducerHandler extends HandlerBase {
  private final PeekTracerHandler myPeekTracerHandler;

  ProducerHandler(@NotNull GenericType afterType) {
    myPeekTracerHandler = new PeekTracerHandler(0, "producer", GenericType.OBJECT, afterType);
  }

  @NotNull
  @Override
  protected List<Variable> getVariables() {
    return myPeekTracerHandler.getVariables();
  }

  @NotNull
  @Override
  public List<IntermediateStreamCall> additionalCallsBefore() {
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public List<IntermediateStreamCall> additionalCallsAfter() {
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
