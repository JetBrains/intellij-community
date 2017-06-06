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
import com.intellij.debugger.streams.wrapper.IntermediateStreamCall;
import com.intellij.debugger.streams.wrapper.ProducerStreamCall;
import com.intellij.debugger.streams.wrapper.TerminatorStreamCall;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public class HandlerFactory {

  @NotNull
  public static TraceExpressionBuilderImpl.IntermediateCallTraceHandler createIntermediate(int number,
                                                                                           @NotNull IntermediateStreamCall call) {
    final String callName = call.getName();
    switch (callName) {
      case "distinct":
        return new DistinctHandler(number, call);
      default:
        return new PeekTracerHandler(number, callName, call.getTypeBefore(), call.getTypeAfter());
    }
  }

  @NotNull
  public static TraceExpressionBuilderImpl.ProducerCallTraceHandler create(@NotNull ProducerStreamCall call) {
    return new ProducerHandler(call.getTypeAfter());
  }

  @NotNull
  public static TraceExpressionBuilderImpl.TerminatorCallTraceHandler create(@NotNull TerminatorStreamCall call,
                                                                             @NotNull String resultExpression) {
    switch (call.getName()) {
      case "allMatch":
      case "anyMatch":
      case "noneMatch":
        return new MatchHandler(call);
      case "max":
      case "min":
      case "findAny":
      case "findFirst":
        return new OptionalTerminatorHandler(call, resultExpression);
    }

    return new TerminatorHandler(call.getTypeBefore());
  }
}
