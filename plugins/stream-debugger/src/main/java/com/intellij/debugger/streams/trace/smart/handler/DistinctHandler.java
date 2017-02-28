package com.intellij.debugger.streams.trace.smart.handler;

import com.intellij.debugger.streams.wrapper.MethodCall;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public class DistinctHandler extends HandlerBase {
  private final PeekTracerHandler myPeekTracer;

  public DistinctHandler(int callNumber, @NotNull String callName) {
    myPeekTracer = new PeekTracerHandler(callNumber, callName);

  }

  @NotNull
  @Override
  public List<MethodCall> additionalCallsBefore() {
    final List<MethodCall> result = new ArrayList<>();
    // TODO: insert calls
    result.addAll(myPeekTracer.additionalCallsBefore());
    return result;
  }

  @NotNull
  @Override
  public List<MethodCall> additionalCallsAfter() {
    return myPeekTracer.additionalCallsAfter();
  }

  @NotNull
  @Override
  public String prepareResult() {
    return doPrepareResult() + myPeekTracer.prepareResult();
  }

  @NotNull
  @Override
  public String getResultExpression() {
    return null;
  }

  @NotNull
  private String doPrepareResult() {
    return "";
  }

  @NotNull
  @Override
  protected List<Variable> getVariables() {
    return null;
  }
}
