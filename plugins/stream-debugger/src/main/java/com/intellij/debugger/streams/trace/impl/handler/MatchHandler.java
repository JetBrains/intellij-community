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
import com.intellij.debugger.streams.wrapper.CallArgument;
import com.intellij.debugger.streams.wrapper.IntermediateStreamCall;
import com.intellij.debugger.streams.wrapper.TerminatorStreamCall;
import com.intellij.debugger.streams.wrapper.impl.CallArgumentImpl;
import com.intellij.debugger.streams.wrapper.impl.IntermediateStreamCallImpl;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public class MatchHandler extends CallTraceHandlerBase {
  private static final String PREDICATE_VARIABLE_NAME = "predicate42";
  private final PeekTracerHandler myBeforeFilterPeekInserter;
  private final PeekTracerHandler myAfterFilterPeekInserter;
  private final GenericType myTypeBefore;
  private final TerminatorStreamCall myCall;

  MatchHandler(@NotNull TerminatorStreamCall call) {
    final GenericType typeBefore = call.getTypeBefore();
    myBeforeFilterPeekInserter = new PeekTracerHandler(0, "match", typeBefore, typeBefore);
    myAfterFilterPeekInserter = new PeekTracerHandler(1, "match", typeBefore, typeBefore);
    myTypeBefore = typeBefore;
    myCall = call;
  }

  @NotNull
  @Override
  protected List<Variable> getVariables() {
    final List<Variable> variables = new ArrayList<>(myBeforeFilterPeekInserter.getVariables());
    variables.addAll(myAfterFilterPeekInserter.getVariables());
    final CallArgument predicate = myCall.getArguments().get(0);
    variables.add(new VariableImpl(predicate.getType(), PREDICATE_VARIABLE_NAME, predicate.getText()));

    return variables;
  }

  @NotNull
  @Override
  public String prepareResult() {
    final String separator = TraceExpressionBuilderImpl.LINE_SEPARATOR;
    return "Object[] result = new Object[3];" + separator +
           "{" + separator +
           myBeforeFilterPeekInserter.prepareResult() +
           "result[0] = " + myBeforeFilterPeekInserter.getResultExpression() + ";" + separator +
           "}" + separator +
           "{" + separator +
           myAfterFilterPeekInserter.prepareResult() + separator +
           "result[1] = " + myAfterFilterPeekInserter.getResultExpression() + ";" + separator +
           "}" + separator +
           "result[2] = streamResult;"
      ;
  }

  @NotNull
  @Override
  public String getResultExpression() {
    return "result";
  }

  @NotNull
  @Override
  public List<IntermediateStreamCall> additionalCallsBefore() {
    final ArrayList<IntermediateStreamCall> calls = new ArrayList<>(myBeforeFilterPeekInserter.additionalCallsBefore());

    String filterPredicate = PREDICATE_VARIABLE_NAME;
    if (myCall.getName().equals("allMatch")) {
      filterPredicate += ".negate()";
    }

    final CallArgumentImpl argument = new CallArgumentImpl(myCall.getArguments().get(0).getType(), filterPredicate);
    calls.add(new IntermediateStreamCallImpl("filter", Collections.singletonList(argument), myTypeBefore,
                                             myTypeBefore, TextRange.EMPTY_RANGE));
    calls.addAll(myAfterFilterPeekInserter.additionalCallsBefore());

    return calls;
  }

  @NotNull
  @Override
  public List<IntermediateStreamCall> additionalCallsAfter() {
    return Collections.emptyList();
  }
}
