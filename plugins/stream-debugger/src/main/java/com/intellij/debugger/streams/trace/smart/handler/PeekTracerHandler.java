package com.intellij.debugger.streams.trace.smart.handler;

import com.intellij.debugger.streams.trace.EvaluateExpressionTracerBase;
import com.intellij.debugger.streams.trace.smart.handler.type.GenericType;
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
    myBeforeVariable = new HashMapVariableImpl(variablePrefix + "before", GenericType.INT, GenericType.OBJECT, true);
    myAfterVariable = new HashMapVariableImpl(variablePrefix + "after", GenericType.INT, GenericType.OBJECT, true);
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
    final String afterMapName = myAfterVariable.getName();
    return Collections.singletonList(new PeekCall(String.format("x -> %s.put(time.incrementAndGet(), x)", afterMapName)));
  }

  @NotNull
  public String getBeforeMapName() {
    return myBeforeVariable.getName();
  }

  @NotNull
  public String getAfterMapName() {
    return myAfterVariable.getName();
  }

  @NotNull
  @Override
  public String prepareResult() {
    final String beforeConversion = myBeforeVariable.convertToArray("beforeArray", true, false);
    final String afterConversion = myAfterVariable.convertToArray("afterArray", true, false);
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
