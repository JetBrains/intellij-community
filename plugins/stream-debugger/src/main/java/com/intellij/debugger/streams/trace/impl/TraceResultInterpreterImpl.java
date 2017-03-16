package com.intellij.debugger.streams.trace.impl;

import com.intellij.debugger.streams.trace.TraceResultInterpreter;
import com.intellij.debugger.streams.trace.TracingResult;
import com.intellij.debugger.streams.trace.smart.resolve.CallTraceResolver;
import com.intellij.debugger.streams.trace.smart.resolve.TraceInfo;
import com.intellij.debugger.streams.trace.smart.resolve.impl.ResolverFactory;
import com.intellij.debugger.streams.trace.smart.resolve.impl.ValuesOrderInfo;
import com.intellij.debugger.streams.wrapper.StreamCall;
import com.intellij.debugger.streams.wrapper.StreamChain;
import com.intellij.openapi.diagnostic.Logger;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.LongValue;
import com.sun.jdi.Value;
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
  public TracingResult interpret(@NotNull StreamChain chain, @NotNull Value evaluationResult) {
    if (evaluationResult instanceof ArrayReference) {
      final ArrayReference resultArray = (ArrayReference)evaluationResult;
      final ArrayReference info = (ArrayReference)resultArray.getValue(0);
      final Value streamResult = resultArray.getValue(1);
      final Value time = resultArray.getValue(2);
      logTime(time);
      final List<TraceInfo> trace = getTrace(chain, info);
      return new TracingResultImpl(streamResult, trace);
    }
    else {
      throw new IllegalArgumentException("result of evaluation must be an ArrayReference");
    }
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
}
