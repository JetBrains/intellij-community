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
  private static final String BEFORE_ARRAY_NAME = "beforeArray";
  private static final String AFTER_ARRAY_NAME = "afterArray";

  private final HashMapVariableImpl myBeforeVariable;
  private final HashMapVariableImpl myAfterVariable;

  PeekTracerHandler(int num, @NotNull String name) {
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
    return Collections.singletonList(new PeekCall(String.format("x -> %s.put(time.get(), x)", afterMapName)));
  }

  @NotNull
  String getAfterMapName() {
    return myAfterVariable.getName();
  }

  @NotNull
  @Override
  public String prepareResult() {
    final String beforeConversion = myBeforeVariable.convertToArray(BEFORE_ARRAY_NAME, true, false);
    final String afterConversion = myAfterVariable.convertToArray(AFTER_ARRAY_NAME, true, false);
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
