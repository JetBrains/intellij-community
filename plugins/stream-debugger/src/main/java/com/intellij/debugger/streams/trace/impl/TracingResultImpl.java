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

import com.intellij.debugger.streams.resolve.ResolvedStreamChain;
import com.intellij.debugger.streams.resolve.ResolverFactory;
import com.intellij.debugger.streams.resolve.ResolverFactoryImpl;
import com.intellij.debugger.streams.resolve.ValuesOrderResolver;
import com.intellij.debugger.streams.resolve.impl.ResolvedProducerCallImpl;
import com.intellij.debugger.streams.resolve.impl.ResolvedStreamChainImpl;
import com.intellij.debugger.streams.resolve.impl.ResolvedTerminatorCallImpl;
import com.intellij.debugger.streams.trace.ResolvedTracingResult;
import com.intellij.debugger.streams.trace.TraceElement;
import com.intellij.debugger.streams.trace.TraceInfo;
import com.intellij.debugger.streams.trace.TracingResult;
import com.intellij.debugger.streams.wrapper.*;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    final ResolverFactory resolverFactory = ResolverFactoryImpl.getInstance();
    List<ValuesOrderResolver.Result> resolvedTraces = myTrace.stream()
      .map(x -> resolverFactory.getResolver(x.getCall().getName()).resolve(x))
      .collect(Collectors.toList());

    final TraceInfo producerTrace = myTrace.get(0);
    ValuesOrderResolver.Result prevResolved =
      resolverFactory.getResolver(producerTrace.getCall().getName()).resolve(producerTrace);
    final List<IntermediateStreamCall> intermediateCalls = mySourceChain.getIntermediateCalls();

    final ResolvedStreamChainImpl.Builder chainBuilder = new ResolvedStreamChainImpl.Builder();
    final List<TraceElement> valuesAfterProducer = TraceUtil.sortedByTime(producerTrace.getValuesOrderAfter().values());
    if (intermediateCalls.isEmpty()) {
      buildResolvedChainWithoutIntermediateCalls(myTrace.get(1), chainBuilder, valuesAfterProducer);
    }
    else {
      final TraceInfo firstIntermediateTrace = myTrace.get(1);
      final ValuesOrderResolver.Result resolve =
        resolverFactory.getResolver(firstIntermediateTrace.getCall().getName()).resolve(firstIntermediateTrace);

      final ProducerStateImpl producerState =
        new ProducerStateImpl(valuesAfterProducer, mySourceChain.getIntermediateCalls().get(0), resolve.getDirectOrder());
      chainBuilder.setProducer(new ResolvedProducerCallImpl(mySourceChain.getProducerCall(), producerState));

      IntermediateCallStateBuilder prevStateBuilder = new IntermediateCallStateBuilder();
      prevStateBuilder.prevCall = mySourceChain.getProducerCall();
      prevStateBuilder.toPrev = resolve.getReverseOrder();
      for (int i = 1; i < myTrace.size() - 1; i++) {
        final TraceInfo info = myTrace.get(i);
        final StreamCall call = info.getCall();

        assert StreamCallType.INTERMEDIATE.equals(call.getType());

        final ValuesOrderResolver.Result result = resolverFactory.getResolver(call.getName()).resolve(info);
        final List<TraceElement> elementsBefore = TraceUtil.sortedByTime(info.getValuesOrderBefore().values());
        final List<TraceElement> elementsAfter = TraceUtil.sortedByTime(info.getValuesOrderAfter().values());
      }
    }

    return new MyResolvedResult(chainBuilder.build());
  }

  private void buildResolvedChainWithoutIntermediateCalls(@NotNull TraceInfo terminatorTrace,
                                                          @NotNull ResolvedStreamChainImpl.Builder chainBuilder,
                                                          @NotNull List<TraceElement> previousTrace) {
    final TerminatorStreamCall terminator = mySourceChain.getTerminationCall();
    final ValuesOrderResolver.Result terminatorResolvedResult =
      ResolverFactoryImpl.getInstance().getResolver(terminator.getName()).resolve(terminatorTrace);
    final ProducerStateImpl producerState =
      new ProducerStateImpl(previousTrace, mySourceChain.getTerminationCall(), terminatorResolvedResult.getDirectOrder());

    final ResolvedProducerCallImpl resolvedProducer = new ResolvedProducerCallImpl(mySourceChain.getProducerCall(), producerState);
    chainBuilder.setProducer(resolvedProducer);

    final TraceElementImpl resultValue = new TraceElementImpl(Integer.MAX_VALUE, myStreamResult);
    final List<TraceElement> after = TraceUtil.sortedByTime(terminatorTrace.getValuesOrderAfter().values());
    final TerminationStateImpl terminatorState =
      new TerminationStateImpl(resultValue, mySourceChain.getProducerCall(), after, terminatorResolvedResult.getReverseOrder());
    final ResolvedTerminatorCallImpl resolvedTerminatorCall =
      new ResolvedTerminatorCallImpl(mySourceChain.getTerminationCall(), producerState, terminatorState);
    chainBuilder.setTerminator(resolvedTerminatorCall);
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

  private static class IntermediateCallStateBuilder {
    List<TraceElement> elements;
    StreamCall prevCall;
    StreamCall nextCall;
    Map<TraceElement, List<TraceElement>> toPrev;
    Map<TraceElement, List<TraceElement>> toNext;

    public IntermediateStateImpl build() {
      return new IntermediateStateImpl(elements, nextCall, prevCall, toPrev, toNext);
    }
  }
}
