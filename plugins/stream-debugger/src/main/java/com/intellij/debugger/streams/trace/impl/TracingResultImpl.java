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
  private final boolean myIsResultException;

  TracingResultImpl(@Nullable Value streamResult, @NotNull List<TraceInfo> trace, boolean isResultException) {
    myStreamResult = streamResult;
    myTrace = trace;
    myIsResultException = isResultException;
  }

  @Nullable
  @Override
  public Value getResult() {
    return myStreamResult;
  }

  @Override
  public boolean exceptionThrown() {
    return myIsResultException;
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

    @Override
    public boolean exceptionThrown() {
      return myIsResultException;
    }

    @Nullable
    @Override
    public Value getResult() {
      return myStreamResult;
    }
  }
}
