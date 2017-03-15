package com.intellij.debugger.streams.trace.smart.handler;

import com.intellij.debugger.streams.trace.EvaluateExpressionTracerBase;
import com.intellij.debugger.streams.trace.smart.handler.type.GenericType;
import com.intellij.debugger.streams.wrapper.IntermediateStreamCall;
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
  private final GenericType myTypeBefore;
  private final GenericType myTypeAfter;

  PeekTracerHandler(int num, @NotNull String name, @NotNull GenericType typeBefore, @NotNull GenericType typeAfter) {
    myTypeBefore = typeBefore;
    myTypeAfter = typeAfter;

    final String variablePrefix = String.format("%sPeek%d", name, num);
    myBeforeVariable = new HashMapVariableImpl(variablePrefix + "before", GenericType.INT, typeBefore, true);
    myAfterVariable = new HashMapVariableImpl(variablePrefix + "after", GenericType.INT, typeAfter, true);
  }

  @NotNull
  @Override
  public List<IntermediateStreamCall> additionalCallsBefore() {
    final String beforeMapName = myBeforeVariable.getName();
    return Collections.singletonList(new PeekCall(String.format("x -> %s.put(time.get(), x)", beforeMapName), myTypeBefore));
  }

  @NotNull
  @Override
  public List<IntermediateStreamCall> additionalCallsAfter() {
    final String afterMapName = myAfterVariable.getName();
    return Collections.singletonList(new PeekCall(String.format("x -> %s.put(time.get(), x)", afterMapName), myTypeAfter));
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
    return "new java.lang.Object[] {beforeArray, afterArray}";
  }

  @NotNull
  @Override
  protected List<Variable> getVariables() {
    return Arrays.asList(myBeforeVariable, myAfterVariable);
  }
}
