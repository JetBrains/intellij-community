// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.impl.interpret;

import com.intellij.debugger.streams.trace.CallTraceInterpreter;
import com.intellij.debugger.streams.trace.TraceElement;
import com.intellij.debugger.streams.trace.TraceInfo;
import com.intellij.debugger.streams.trace.impl.interpret.ex.UnexpectedArrayLengthException;
import com.intellij.debugger.streams.trace.impl.interpret.ex.UnexpectedValueException;
import com.intellij.debugger.streams.trace.impl.interpret.ex.UnexpectedValueTypeException;
import com.intellij.debugger.streams.wrapper.StreamCall;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.IntegerValue;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Vitaliy.Bibaev
 */
public class DistinctCallTraceInterpreter implements CallTraceInterpreter {
  private final CallTraceInterpreter myPeekResolver = new SimplePeekCallTraceInterpreter();

  private enum Direction {
    DIRECT, REVERSE
  }

  @Override
  public @NotNull TraceInfo resolve(@NotNull StreamCall call, @NotNull Value value) {
    if (value instanceof ArrayReference) {
      final Value peekTrace = ((ArrayReference)value).getValue(0);
      final Value trace = ((ArrayReference)value).getValue(1);

      final TraceInfo order = myPeekResolver.resolve(call, peekTrace);

      final Map<TraceElement, List<TraceElement>> direct = resolve(trace, order, Direction.DIRECT);
      final Map<TraceElement, List<TraceElement>> reverse = resolve(trace, order, Direction.REVERSE);

      return new MyDistinctInfo(order, direct, reverse);
    }

    throw new UnexpectedValueException("distinct trace must be an array value");
  }

  private static @NotNull Map<TraceElement, List<TraceElement>> resolve(@NotNull Value value,
                                                                        @NotNull TraceInfo order,
                                                                        @NotNull Direction direction) {
    if (value instanceof ArrayReference convertedMap) {
      final Value keys = convertedMap.getValue(0);
      final Value values = convertedMap.getValue(1);
      if (keys instanceof ArrayReference keysArray && values instanceof ArrayReference valuesArray) {
        return Direction.DIRECT.equals(direction)
               ? resolveDirectTrace(keysArray, valuesArray, order)
               : resolveReverseTrace(keysArray, valuesArray, order);
      }

      throw new UnexpectedValueException("keys and values arrays must be arrays");
    }

    throw new UnexpectedValueException("value must be an array reference");
  }

  private static @NotNull Map<TraceElement, List<TraceElement>> resolveDirectTrace(@NotNull ArrayReference keys,
                                                                                   @NotNull ArrayReference values,
                                                                                   @NotNull TraceInfo order) {
    final int size = keys.length();
    if (size != values.length()) {
      throw new UnexpectedArrayLengthException("length of keys array should be same with values array");
    }

    final Map<TraceElement, List<TraceElement>> result = new HashMap<>();

    final Map<Integer, TraceElement> before = order.getValuesOrderBefore();
    final Map<Integer, TraceElement> after = order.getValuesOrderAfter();
    for (int i = 0; i < size; i++) {
      final int fromTime = extractIntValue(keys.getValue(i));
      final int afterTime = extractIntValue(values.getValue(i));
      result.put(before.get(fromTime), Collections.singletonList(after.get(afterTime)));
    }

    return result;
  }

  private static @NotNull Map<TraceElement, List<TraceElement>> resolveReverseTrace(@NotNull ArrayReference keys,
                                                                                    @NotNull ArrayReference values,
                                                                                    @NotNull TraceInfo order) {
    final int size = keys.length();
    if (size != values.length()) {
      throw new UnexpectedArrayLengthException("length of keys array should be same with values array");
    }

    final Map<TraceElement, List<TraceElement>> result = new HashMap<>();

    final Map<Integer, TraceElement> before = order.getValuesOrderBefore();
    final Map<Integer, TraceElement> after = order.getValuesOrderAfter();
    for (int i = 0; i < size; i++) {
      final int fromTime = extractIntValue(keys.getValue(i));
      final int afterTime = extractIntValue(values.getValue(i));
      final TraceElement beforeElement = before.get(fromTime);
      final TraceElement afterElement = after.get(afterTime);
      result.computeIfAbsent(afterElement, x -> new ArrayList<>()).add(beforeElement);
    }

    return result;
  }

  private static final class MyDistinctInfo extends ValuesOrderInfo {
    private final Map<TraceElement, List<TraceElement>> myDirectTrace;
    private final Map<TraceElement, List<TraceElement>> myReverseTrace;

    private MyDistinctInfo(@NotNull TraceInfo info,
                           @NotNull Map<TraceElement, List<TraceElement>> directTrace,
                           @NotNull Map<TraceElement, List<TraceElement>> reverseTrace) {
      super(info.getCall(), info.getValuesOrderBefore(), info.getValuesOrderAfter());
      myDirectTrace = directTrace;
      myReverseTrace = reverseTrace;
    }

    @Override
    public @Nullable Map<TraceElement, List<TraceElement>> getDirectTrace() {
      return Collections.unmodifiableMap(myDirectTrace);
    }

    @Override
    public @Nullable Map<TraceElement, List<TraceElement>> getReverseTrace() {
      return Collections.unmodifiableMap(myReverseTrace);
    }
  }

  private static int extractIntValue(@NotNull Value value) {
    if (value instanceof IntegerValue) {
      return ((IntegerValue)value).value();
    }

    throw new UnexpectedValueTypeException("value should be IntegerValue, but actual is " + value.type().name());
  }
}
