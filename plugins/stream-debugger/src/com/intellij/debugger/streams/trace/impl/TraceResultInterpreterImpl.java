// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.impl;

import com.intellij.debugger.streams.lib.InterpreterFactory;
import com.intellij.debugger.streams.trace.CallTraceInterpreter;
import com.intellij.debugger.streams.trace.TraceInfo;
import com.intellij.debugger.streams.trace.TraceResultInterpreter;
import com.intellij.debugger.streams.trace.TracingResult;
import com.intellij.debugger.streams.trace.impl.interpret.ValuesOrderInfo;
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
  private final InterpreterFactory myInterpreterFactory;

  public TraceResultInterpreterImpl(@NotNull InterpreterFactory interpreterFactory) {
    myInterpreterFactory = interpreterFactory;
  }

  @NotNull
  @Override
  public TracingResult interpret(@NotNull StreamChain chain, @NotNull ArrayReference resultArray) {
    final ArrayReference info = (ArrayReference)resultArray.getValue(0);
    final ArrayReference result = (ArrayReference)resultArray.getValue(1);
    final Value streamResult = result.getValue(0);
    final Value time = resultArray.getValue(2);
    logTime(time);
    final List<TraceInfo> trace = getTrace(chain, info);
    return new TracingResultImpl(chain, TraceElementImpl.ofResultValue(streamResult), trace, isException(result));
  }

  @NotNull
  private List<TraceInfo> getTrace(@NotNull StreamChain chain, @NotNull ArrayReference info) {
    final int callCount = chain.length();
    final List<TraceInfo> result = new ArrayList<>(callCount);

    for (int i = 0; i < callCount; i++) {
      final StreamCall call = chain.getCall(i);
      final Value trace = info.getValue(i);
      final CallTraceInterpreter interpreter = myInterpreterFactory.getInterpreter(call.getName());

      final TraceInfo traceInfo = trace == null ? ValuesOrderInfo.empty(call) : interpreter.resolve(call, trace);
      result.add(traceInfo);
    }

    return result;
  }

  private static void logTime(@NotNull Value elapsedTimeArray) {
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
