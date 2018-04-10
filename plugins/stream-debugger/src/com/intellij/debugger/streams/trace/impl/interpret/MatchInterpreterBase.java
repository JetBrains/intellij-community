// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.impl.interpret;

import com.intellij.debugger.streams.trace.CallTraceInterpreter;
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
public abstract class MatchInterpreterBase implements CallTraceInterpreter {
  private final CallTraceInterpreter myPeekResolver = new SimplePeekCallTraceInterpreter();

  @NotNull
  @Override
  public TraceInfo resolve(@NotNull StreamCall call, @NotNull Value value) {
    if (value instanceof ArrayReference) {
      final ArrayReference array = (ArrayReference)value;
      if (array.length() != 2) {
        throw new UnexpectedArrayLengthException("trace array for *match call should contain two items. Actual = " + array.length());
      }

      final Value peeksResult = array.getValue(0);
      final Value streamResult = array.getValue(1);
      final TraceElement streamResultElement = TraceElementImpl.ofResultValue(((ArrayReference)streamResult).getValue(0));

      final TraceInfo peeksInfo = myPeekResolver.resolve(call, peeksResult);

      final Collection<TraceElement> traceBeforeFilter = peeksInfo.getValuesOrderBefore().values();
      final Map<Integer, TraceElement> traceAfter = peeksInfo.getValuesOrderAfter();
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
  private static Map<Integer, TraceElement> onlyFiltered(@NotNull Collection<TraceElement> afterFilter) {
    return makeIndexByTime(afterFilter.stream());
  }

  @NotNull
  private static Map<Integer, TraceElement> difference(@NotNull Collection<TraceElement> before, @NotNull Set<Integer> timesAfter) {
    return makeIndexByTime(before.stream().filter(x -> !timesAfter.contains(x.getTime())));
  }

  @NotNull
  private static Map<Integer, TraceElement> makeIndexByTime(@NotNull Stream<TraceElement> elementStream) {
    return elementStream.collect(Collectors.toMap(TraceElement::getTime, Function.identity()));
  }
}
