/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.debugger.streams.trace.impl.handler;

import com.intellij.debugger.streams.trace.impl.TraceExpressionBuilderImpl;
import com.intellij.debugger.streams.trace.impl.handler.type.GenericType;
import com.intellij.debugger.streams.wrapper.IntermediateStreamCall;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public class PeekTracerHandler extends HandlerBase.Intermediate {
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
    final String beforeConversion = myBeforeVariable.convertToArray(BEFORE_ARRAY_NAME);
    final String afterConversion = myAfterVariable.convertToArray(AFTER_ARRAY_NAME);
    return beforeConversion + TraceExpressionBuilderImpl.LINE_SEPARATOR + afterConversion;
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
