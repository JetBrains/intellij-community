package com.intellij.debugger.streams.trace.impl;

import com.intellij.debugger.streams.resolve.ResolvedTrace;
import com.intellij.debugger.streams.resolve.ResolvedTraceImpl;
import com.intellij.debugger.streams.resolve.ResolverFactoryImpl;
import com.intellij.debugger.streams.resolve.ValuesOrderResolver;
import com.intellij.debugger.streams.trace.ResolvedTracingResult;
import com.intellij.debugger.streams.trace.TracingResult;
import com.intellij.debugger.streams.trace.TraceElement;
import com.intellij.debugger.streams.trace.TraceInfo;
import com.intellij.debugger.streams.wrapper.StreamCall;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public class TracingResultImpl implements TracingResult {
  private final Value myStreamResult;
  private final List<TraceInfo> myTrace;

  TracingResultImpl(@NotNull Value streamResult, @NotNull List<TraceInfo> trace) {
    myStreamResult = streamResult;
    myTrace = trace;
  }

  @Nullable
  @Override
  public Value getResult() {
    return myStreamResult;
  }

  @NotNull
  @Override
  public List<TraceInfo> getTrace() {
    return myTrace;
  }

  @NotNull
  @Override
  public ResolvedTracingResult resolve() {
    if (myTrace.size() == 0) {
      return new MyResolvedResult(Collections.emptyList());
    }

    final List<ResolvedTrace> result = new ArrayList<>();
    final TraceInfo producerTrace = myTrace.get(0);
    ValuesOrderResolver.Result prevResolved =
      ResolverFactoryImpl.getInstance().getResolver(producerTrace.getCall().getName()).resolve(producerTrace);

    for (int i = 1; i < myTrace.size(); i++) {
      final TraceInfo traceInfo = myTrace.get(i);
      final StreamCall currentCall = traceInfo.getCall();

      final ValuesOrderResolver resolver = ResolverFactoryImpl.getInstance().getResolver(currentCall.getName());
      final ValuesOrderResolver.Result currentResolve = resolver.resolve(traceInfo);

      final Collection<TraceElement> values = traceInfo.getValuesOrderBefore().values();
      final ResolvedTrace resolvedTrace =
        new ResolvedTraceImpl(currentCall, values, prevResolved.getReverseOrder(), currentResolve.getDirectOrder());
      result.add(resolvedTrace);

      prevResolved = currentResolve;
    }

    return new MyResolvedResult(result);
  }

  private class MyResolvedResult implements ResolvedTracingResult {
    private final List<ResolvedTrace> myTrace;

    MyResolvedResult(@NotNull List<ResolvedTrace> trace) {
      myTrace = trace;
    }

    @NotNull
    @Override
    public List<ResolvedTrace> getResolvedTraces() {
      return Collections.unmodifiableList(myTrace);
    }

    @Nullable
    @Override
    public Value getResult() {
      return myStreamResult;
    }
  }
}
