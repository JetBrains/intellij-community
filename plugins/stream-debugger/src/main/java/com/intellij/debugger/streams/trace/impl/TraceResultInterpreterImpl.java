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
package com.intellij.debugger.streams.trace.impl;

import com.intellij.debugger.streams.trace.TraceResultInterpreter;
import com.intellij.debugger.streams.trace.TracingResult;
import com.intellij.debugger.streams.trace.CallTraceResolver;
import com.intellij.debugger.streams.trace.TraceInfo;
import com.intellij.debugger.streams.trace.impl.resolve.ResolverFactory;
import com.intellij.debugger.streams.trace.impl.resolve.ValuesOrderInfo;
import com.intellij.debugger.streams.wrapper.StreamCall;
import com.intellij.debugger.streams.wrapper.StreamChain;
import com.intellij.openapi.diagnostic.Logger;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author Vitaliy.Bibaev
 */
public class TraceResultInterpreterImpl implements TraceResultInterpreter {
  private static final Logger LOG = Logger.getInstance(TraceResultInterpreterImpl.class);

  @NotNull
  @Override
  public TracingResult interpret(@NotNull StreamChain chain, @NotNull ArrayReference resultArray) {
    final ArrayReference info = (ArrayReference)resultArray.getValue(0);
    final ArrayReference result = (ArrayReference)resultArray.getValue(1);
    final Value streamResult = result.getValue(0);
    final Value time = resultArray.getValue(2);
    logTime(time);
    final List<TraceInfo> trace = getTrace(chain, info);
    return new TracingResultImpl(streamResult, trace, isException(result));
  }

  @NotNull
  private List<TraceInfo> getTrace(@NotNull StreamChain chain, @NotNull ArrayReference info) {
    final int callCount = chain.length();
    final List<TraceInfo> result = new ArrayList<>(callCount);
    for (int i = 0; i < callCount; i++) {
      final StreamCall call = chain.getCall(i);
      final Value trace = info.getValue(i);
      final CallTraceResolver resolver = ResolverFactory.getInstance().getResolver(call.getName());
      final TraceInfo traceInfo = trace == null ? ValuesOrderInfo.empty(call) : resolver.resolve(call, trace);
      result.add(traceInfo);
    }

    return result;
  }

  private void logTime(@NotNull Value elapsedTimeArray) {
    final Value elapsedTime = ((ArrayReference)elapsedTimeArray).getValue(0);
    final long elapsedNanoseconds = ((LongValue)elapsedTime).value();
    final long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(elapsedNanoseconds);
    LOG.info("evaluation completed in " + elapsedMillis + "ms");
  }

  private static boolean isException(@NotNull ArrayReference result) {
    final ReferenceType type = result.referenceType();
    if (type instanceof ArrayType) {
      if (((ArrayType)type).componentTypeName().contains("Throwable")) {
        return true;
      }
    }

    return false;
  }
}
