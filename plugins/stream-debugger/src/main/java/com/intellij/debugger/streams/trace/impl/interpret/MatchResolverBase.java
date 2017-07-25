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
package com.intellij.debugger.streams.trace.impl.interpret;

import com.intellij.debugger.streams.trace.CallTraceResolver;
import com.intellij.debugger.streams.trace.TraceElement;
import com.intellij.debugger.streams.trace.TraceInfo;
import com.intellij.debugger.streams.trace.impl.TraceElementImpl;
import com.intellij.debugger.streams.trace.impl.interpret.ex.UnexpectedArrayLengthException;
import com.intellij.debugger.streams.trace.impl.interpret.ex.UnexpectedValueTypeException;
import com.intellij.debugger.streams.wrapper.StreamCall;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Vitaliy.Bibaev
 */
public abstract class MatchResolverBase implements CallTraceResolver {
  private final CallTraceResolver myPeekResolver = new SimplePeekCallTraceResolver();

  @NotNull
  @Override
  public TraceInfo resolve(@NotNull StreamCall call, @NotNull Value value) {
    if (value instanceof ArrayReference) {
      final ArrayReference array = (ArrayReference)value;
      if (array.length() != 3) {
        throw new UnexpectedArrayLengthException("trace array for *match call should contain two items. Actual = " + array.length());
      }

      final Value beforeFilter = array.getValue(0);
      final Value afterFilter = array.getValue(1);
      final Value streamResult = array.getValue(2);
      final TraceElement streamResultElement = TraceElementImpl.ofResultValue(((ArrayReference)streamResult).getValue(0));

      final TraceInfo beforeFilterInfo = myPeekResolver.resolve(call, beforeFilter);
      final TraceInfo afterFilterInfo = myPeekResolver.resolve(call, afterFilter);

      final Collection<TraceElement> traceBeforeFilter = beforeFilterInfo.getValuesOrderBefore().values();
      final Map<Integer, TraceElement> traceAfter = afterFilterInfo.getValuesOrderBefore();
      final Collection<TraceElement> traceAfterFilter = traceAfter.values();

      final boolean result = getResult(traceBeforeFilter, traceAfterFilter);
      final Action action = getAction(result);

      final Map<Integer, TraceElement> beforeTrace =
        Action.CONNECT_FILTERED.equals(action) ? onlyFiltered(traceAfterFilter) : difference(traceBeforeFilter, traceAfter.keySet());

      return new ValuesOrderInfo(call, beforeTrace, makeIndexByTime(Stream.of(streamResultElement)));
    }

    throw new UnexpectedValueTypeException("value should be array reference, but given " + value.type().toString());
  }

  protected abstract boolean getResult(@NotNull Collection<TraceElement> traceBeforeFilter,
                                       @NotNull Collection<TraceElement> traceAfterFilter);

  @NotNull
  protected abstract Action getAction(boolean result);

  protected enum Action {
    CONNECT_FILTERED, CONNECT_DIFFERENCE
  }

  @NotNull
  private Map<Integer, TraceElement> onlyFiltered(@NotNull Collection<TraceElement> afterFilter) {
    return makeIndexByTime(afterFilter.stream());
  }

  @NotNull
  private Map<Integer, TraceElement> difference(@NotNull Collection<TraceElement> before, @NotNull Set<Integer> timesAfter) {
    return makeIndexByTime(before.stream().filter(x -> !timesAfter.contains(x.getTime())));
  }

  @NotNull
  private static Map<Integer, TraceElement> makeIndexByTime(@NotNull Stream<TraceElement> elementStream) {
    return elementStream.collect(Collectors.toMap(TraceElement::getTime, Function.identity()));
  }
}
