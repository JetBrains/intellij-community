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
import com.intellij.debugger.streams.wrapper.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public class HandlerFactory {

  @NotNull
  public static TraceExpressionBuilderImpl.StreamCallTraceHandler createIntermediate(int number,
                                                                                     @NotNull IntermediateStreamCall call) {
    final String callName = call.getName();
    switch (callName) {
      case "distinct":
        return new DistinctHandler(number, call);
      default:
        return new PeekTracerHandler(number, callName, call.getTypeBefore(), call.getTypeAfter());
    }
  }

  public static TraceExpressionBuilderImpl.StreamCallTraceHandler create(@NotNull ProducerStreamCall call) {
    return new ProducerHandler(call.getTypeAfter());
  }

  public static TraceExpressionBuilderImpl.StreamCallTraceHandler create(@NotNull TerminatorStreamCall call) {
    return new TerminatorHandler(call.getTypeBefore());
  }
}
