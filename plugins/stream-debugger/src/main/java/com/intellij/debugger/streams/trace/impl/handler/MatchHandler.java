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

import com.intellij.debugger.streams.trace.impl.handler.type.GenericType;
import com.intellij.debugger.streams.wrapper.IntermediateStreamCall;
import com.intellij.debugger.streams.wrapper.TerminatorStreamCall;
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
  private final PeekTracerHandler myBeforeFilterPeekInserter;
  private final PeekTracerHandler myAfterFilterPeekInserter;
  private final GenericType myTypeBefore;

  MatchHandler(@NotNull TerminatorStreamCall call) {
    final GenericType typeBefore = call.getTypeBefore();
    myBeforeFilterPeekInserter = new PeekTracerHandler(0, "match", typeBefore, typeBefore);
    myAfterFilterPeekInserter = new PeekTracerHandler(1, "match", typeBefore, typeBefore);
    myTypeBefore = typeBefore;
  }

  @NotNull
  @Override
  protected List<Variable> getVariables() {
    final List<Variable> variables = new ArrayList<>(myBeforeFilterPeekInserter.getVariables());
    variables.addAll(myAfterFilterPeekInserter.getVariables());
    return variables;
  }

  @NotNull
  @Override
  public String prepareResult() {
    return myBeforeFilterPeekInserter.prepareResult() + myAfterFilterPeekInserter.prepareResult();
  }

  @NotNull
  @Override
  public String getResultExpression() {
    return "new Object[] { " +
           myBeforeFilterPeekInserter.getResultExpression() +
           ", " +
           myAfterFilterPeekInserter.getResultExpression() +
           "}";
  }

  @NotNull
  @Override
  public List<IntermediateStreamCall> additionalCallsBefore() {
    final ArrayList<IntermediateStreamCall> calls = new ArrayList<>(myBeforeFilterPeekInserter.additionalCallsBefore());
    calls.add(new IntermediateStreamCallImpl("filter", "", myTypeBefore, myTypeBefore, TextRange.EMPTY_RANGE));
    calls.addAll(myAfterFilterPeekInserter.additionalCallsBefore());

    return calls;
  }

  @NotNull
  @Override
  public List<IntermediateStreamCall> additionalCallsAfter() {
    return Collections.emptyList();
  }
}
