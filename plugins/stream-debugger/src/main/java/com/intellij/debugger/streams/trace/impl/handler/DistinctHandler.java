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
import com.intellij.debugger.streams.trace.impl.handler.type.ClassTypeImpl;
import com.intellij.debugger.streams.trace.impl.handler.type.GenericType;
import com.intellij.debugger.streams.wrapper.IntermediateStreamCall;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public class DistinctHandler extends HandlerBase.Intermediate {
  private final PeekTracerHandler myPeekTracer;
  private final HashMapVariableImpl myStoreMapVariable;
  private final HashMapVariableImpl myResolveMapVariable;
  private final HashMapVariableImpl myReverseUtilMapVariable;
  private final GenericType myBeforeType;
  private final GenericType myAfterType;

  DistinctHandler(int callNumber, @NotNull IntermediateStreamCall call) {
    myPeekTracer = new PeekTracerHandler(callNumber, "distinct", call.getTypeBefore(), call.getTypeAfter());

    myBeforeType = call.getTypeBefore();
    myAfterType = call.getTypeAfter();
    final String variablePrefix = "distinct" + callNumber;
    final String storeMapType = String.format("java.util.Map<java.lang.Integer, %s>", myBeforeType.getGenericTypeName());
    myStoreMapVariable =
      new HashMapVariableImpl(variablePrefix + "Store", GenericType.OBJECT, new ClassTypeImpl(storeMapType), false);
    myResolveMapVariable = new HashMapVariableImpl(variablePrefix + "Resolve", GenericType.INT, GenericType.INT, false);
    myReverseUtilMapVariable = new HashMapVariableImpl(variablePrefix + "ReverseUtil", GenericType.INT, GenericType.INT, false);
  }

  @NotNull
  @Override
  public List<IntermediateStreamCall> additionalCallsBefore() {
    final List<IntermediateStreamCall> result = new ArrayList<>(myPeekTracer.additionalCallsBefore());

    final PeekCall storeCall = new PeekCall(createStoreLambda(), myBeforeType);
    result.add(storeCall);
    return result;
  }

  @NotNull
  @Override
  public List<IntermediateStreamCall> additionalCallsAfter() {
    final List<IntermediateStreamCall> result = new ArrayList<>(myPeekTracer.additionalCallsAfter());
    result.add(new PeekCall(createResolveLambda(), myAfterType));
    return result;
  }

  @NotNull
  @Override
  public String prepareResult() {
    final String newLine = TraceExpressionBuilderImpl.LINE_SEPARATOR;
    final String peekPrepare = myPeekTracer.prepareResult();

    final String storeMapName = myStoreMapVariable.getName();
    final String afterMapName = myPeekTracer.getAfterMapName();
    final String beforeValueType = myStoreMapVariable.getValueType().getVariableTypeName();
    final String prepareResolveMap =
      "{" + newLine +
      "  for (final int timeAfter : " + myReverseUtilMapVariable.getName() + ".keySet()) {" + newLine +
      "    final java.lang.Object afterValue = " + afterMapName + ".get(timeAfter);" + newLine +
      "    final " + beforeValueType + " valuesBefore = " + storeMapName + ".get(afterValue);" + newLine +
      "    for (final int timeBefore : valuesBefore.keySet()) {" + newLine +
      "      " + myResolveMapVariable.getName() + ".put(timeBefore, timeAfter);" + newLine +
      "    }" + newLine +
      "  }" + newLine +
      "}" + newLine;

    final String peekResult =
      "final java.lang.Object peekResult = " + myPeekTracer.getResultExpression() + ";" + TraceExpressionBuilderImpl.LINE_SEPARATOR;
    final String resolve2Array = myResolveMapVariable.convertToArray("resolve");
    return peekPrepare + prepareResolveMap + resolve2Array + peekResult;
  }

  @NotNull
  @Override
  public String getResultExpression() {
    return "new java.lang.Object[] { peekResult, resolve }";
  }

  @NotNull
  private String createStoreLambda() {
    final String storeMap = myStoreMapVariable.getName();
    return "x -> " + String.format("%s.computeIfAbsent(x, y -> new java.util.LinkedHashMap<>()).put(time.get(), x)", storeMap);
  }

  @NotNull
  private String createResolveLambda() {
    final String newLine = TraceExpressionBuilderImpl.LINE_SEPARATOR;
    final String storeMap = myStoreMapVariable.getName();
    final String resolveReverseMap = myReverseUtilMapVariable.getName();
    final GenericType storeValueType = myStoreMapVariable.getValueType();

    return "x -> {" + newLine +
           "  final " + storeValueType.getVariableTypeName() + " objects = " + String.format("%s.get(x);", storeMap) + newLine +
           "  for (final int key: objects.keySet()) {" + newLine +
           "    final " + myBeforeType.getVariableTypeName() + " value = objects.get(key);" + newLine +
           "    if (value == x && !" + resolveReverseMap + ".containsKey(key)) {" + newLine +
           "      " + String.format("%s.put(time.get(), key);", resolveReverseMap) + newLine +
           "    }" + newLine +
           "  }" + newLine +
           "}" + newLine;
  }

  @NotNull
  @Override
  protected List<Variable> getVariables() {
    final List<Variable> variables =
      new ArrayList<>(Arrays.asList(myStoreMapVariable, myResolveMapVariable, myReverseUtilMapVariable));
    variables.addAll(myPeekTracer.getVariables());
    return variables;
  }
}
