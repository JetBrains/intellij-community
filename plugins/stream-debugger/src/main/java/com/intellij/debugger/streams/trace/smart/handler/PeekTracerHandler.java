package com.intellij.debugger.streams.trace.smart.handler;

import com.intellij.debugger.streams.trace.EvaluateExpressionTracerBase;
import com.intellij.debugger.streams.wrapper.StreamCall;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public class PeekTracerHandler extends HandlerBase {
  private final HashMapVariableImpl myBeforeVariable;
  private final HashMapVariableImpl myAfterVariable;

  public PeekTracerHandler(int num, @NotNull String name) {
    final String variablePrefix = String.format("%sPeek%d", name, num);
    myBeforeVariable = new HashMapVariableImpl(variablePrefix + "before", "Integer", "Object", true);
    myAfterVariable = new HashMapVariableImpl(variablePrefix + "after", "Integer", "Object", true);
  }

  @NotNull
  @Override
  public List<StreamCall> additionalCallsBefore() {
    final String beforeMapName = myBeforeVariable.getName();
    return Collections.singletonList(new PeekCall(String.format("x -> %s.put(time.get(), x)", beforeMapName)));
  }

  @NotNull
  @Override
  public List<StreamCall> additionalCallsAfter() {
    final String afterMapName = myBeforeVariable.getName();
    return Collections.singletonList(new PeekCall(String.format("x -> %s.put(time.incrementAndGet(), x)", afterMapName)));
  }

  @NotNull
  @Override
  public String prepareResult() {
    final String beforeConversion = myBeforeVariable.convertToArray("beforeArray");
    final String afterConversion = myAfterVariable.convertToArray("afterArray");
    return beforeConversion + EvaluateExpressionTracerBase.LINE_SEPARATOR + afterConversion;
  }

  @NotNull
  @Override
  public String getResultExpression() {
    return "new Object[] {beforeArray, afterArray}";
  }

  @NotNull
  @Override
  protected List<Variable> getVariables() {
    return Arrays.asList(myBeforeVariable, myAfterVariable);
  }
}
