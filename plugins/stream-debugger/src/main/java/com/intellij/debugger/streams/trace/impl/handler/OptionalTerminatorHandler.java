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
import com.intellij.debugger.streams.trace.impl.handler.type.GenericTypeUtil;
import com.intellij.debugger.streams.wrapper.IntermediateStreamCall;
import com.intellij.debugger.streams.wrapper.TerminatorStreamCall;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Vitaliy.Bibaev
 */
public class OptionalTerminatorHandler implements TraceExpressionBuilderImpl.TerminatorCallTraceHandler {
  private static final Map<GenericType, String> TYPE_2_DEFAULT_VALUE = new HashMap<>();

  static {
    TYPE_2_DEFAULT_VALUE.put(GenericType.INT, "0");
    TYPE_2_DEFAULT_VALUE.put(GenericType.DOUBLE, "0.");
    TYPE_2_DEFAULT_VALUE.put(GenericType.LONG, "0L");
    TYPE_2_DEFAULT_VALUE.put(GenericType.OBJECT, "null");
  }

  private final TerminatorHandler myTerminatorHandler;
  private final GenericType myOptionalType;
  private final String myResultExpression;

  OptionalTerminatorHandler(@NotNull TerminatorStreamCall call, @NotNull String resultExpression) {
    myTerminatorHandler = new TerminatorHandler(call.getTypeBefore());
    myOptionalType = GenericTypeUtil.unwrapOptional(call.getResultType());
    myResultExpression = resultExpression;
  }

  @NotNull
  @Override
  public String additionalVariablesDeclaration() {
    return myTerminatorHandler.additionalVariablesDeclaration();
  }

  @NotNull
  @Override
  public String prepareResult() {
    return myTerminatorHandler.prepareResult();
  }

  @NotNull
  @Override
  public String getResultExpression() {
    return "new Object[] { " + myTerminatorHandler.getResultExpression() + ", " + buildOptionalExpression() + "}";
  }

  @NotNull
  @Override
  public List<IntermediateStreamCall> additionalCallsBefore() {
    return myTerminatorHandler.additionalCallsBefore();
  }

  @NotNull
  private String buildOptionalExpression() {
    final String optionalContentExpression = String.format("new %s[] { %s.orElse(%s)}", myOptionalType.getVariableTypeName(),
                                                           myResultExpression,
                                                           TYPE_2_DEFAULT_VALUE.get(myOptionalType));
    return String.format("new Object[] { new boolean[] { %s.isPresent() }, %s }", myResultExpression, optionalContentExpression);
  }
}
