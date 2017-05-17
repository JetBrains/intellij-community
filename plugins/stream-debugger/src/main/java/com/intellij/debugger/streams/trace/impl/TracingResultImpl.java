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

import com.intellij.debugger.streams.resolve.*;
import com.intellij.debugger.streams.resolve.impl.ResolvedStreamChainImpl;
import com.intellij.debugger.streams.trace.ResolvedTracingResult;
import com.intellij.debugger.streams.trace.TraceElement;
import com.intellij.debugger.streams.trace.TraceInfo;
import com.intellij.debugger.streams.trace.TracingResult;
import com.intellij.debugger.streams.wrapper.IntermediateStreamCall;
import com.intellij.debugger.streams.wrapper.StreamCall;
import com.intellij.debugger.streams.wrapper.StreamChain;
import com.intellij.debugger.streams.wrapper.TraceUtil;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public class TracingResultImpl implements TracingResult {
  private final Value myStreamResult;
  private final List<TraceInfo> myTrace;
  private final boolean myIsResultException;
  private final StreamChain mySourceChain;

  TracingResultImpl(@NotNull StreamChain chain, @Nullable Value streamResult, @NotNull List<TraceInfo> trace, boolean isResultException) {
    myStreamResult = streamResult;
    myTrace = trace;
    mySourceChain = chain;
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
    assert myTrace.size() == mySourceChain.length();

    final TraceInfo producerTrace = myTrace.get(0);
    ValuesOrderResolver.Result prevResolved =
      ResolverFactoryImpl.getInstance().getResolver(producerTrace.getCall().getName()).resolve(producerTrace);
    final List<IntermediateStreamCall> intermediateCalls = mySourceChain.getIntermediateCalls();
    final StreamCall nextCall = intermediateCalls.isEmpty() ? mySourceChain.getTerminationCall() : intermediateCalls.get(0);

    final ResolvedStreamChainImpl.Builder chainBuilder = new ResolvedStreamChainImpl.Builder();
    for (int i = 1; i < myTrace.size(); i++) {
      final TraceInfo traceInfo = myTrace.get(i);
      final StreamCall currentCall = traceInfo.getCall();

      final ValuesOrderResolver resolver = ResolverFactoryImpl.getInstance().getResolver(currentCall.getName());
      final ValuesOrderResolver.Result currentResolve = resolver.resolve(traceInfo);

      final Collection<TraceElement> values = TraceUtil.sortedByTime(traceInfo.getValuesOrderBefore().values());
      final ResolvedTrace resolvedTrace =
        new ResolvedTraceImpl(currentCall, values, prevResolved.getReverseOrder(), currentResolve.getDirectOrder());

      prevResolved = currentResolve;
    }

    // TODO: construct chain
    return new MyResolvedResult(chainBuilder.build());
  }

  private class MyResolvedResult implements ResolvedTracingResult {

    @NotNull private final ResolvedStreamChain myChain;

    MyResolvedResult(@NotNull ResolvedStreamChain resolvedStreamChain) {
      myChain = resolvedStreamChain;
    }

    @NotNull
    @Override
    public ResolvedStreamChain getResolvedChain() {
      return myChain;
    }

    @NotNull
    @Override
    public StreamChain getSourceChain() {
      return mySourceChain;
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
