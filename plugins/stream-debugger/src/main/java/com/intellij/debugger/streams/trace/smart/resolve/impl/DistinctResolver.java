package com.intellij.debugger.streams.trace.smart.resolve.impl;

import com.intellij.debugger.streams.trace.smart.TraceElement;
import com.intellij.debugger.streams.trace.smart.resolve.TraceInfo;
import com.intellij.debugger.streams.trace.smart.resolve.TraceResolver;
import com.intellij.debugger.streams.trace.smart.resolve.ex.UnexpectedArrayLengthException;
import com.intellij.debugger.streams.trace.smart.resolve.ex.UnexpectedValueException;
import com.intellij.debugger.streams.trace.smart.resolve.ex.UnexpectedValueTypeException;
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
public class DistinctResolver implements TraceResolver {
  private final TraceResolver myPeekResolver = new SimplePeekResolver();

  @NotNull
  @Override
  public TraceInfo resolve(@NotNull StreamCall call, @NotNull Value value) {
    if (value instanceof ArrayReference) {
      final Value peekTrace = ((ArrayReference)value).getValue(0);
      final Value distinctDirectTrace = ((ArrayReference)value).getValue(1);
      final Value distinctReverseTrace = ((ArrayReference)value).getValue(2);

      final TraceInfo order = myPeekResolver.resolve(call, peekTrace);

      final Map<TraceElement, List<TraceElement>> direct = resolveDirect(distinctDirectTrace, order);
      final Map<TraceElement, List<TraceElement>> reverse = resolveReverse(distinctReverseTrace, order);

      return new MyDistinctInfo(order, direct, reverse);
    }

    throw new UnexpectedValueException("distinct trace must be an array value");
  }

  private static Map<TraceElement, List<TraceElement>> resolveDirect(@NotNull Value value,
                                                                     @NotNull TraceInfo order) {
    if (value instanceof ArrayReference) {
      final ArrayReference convertedMap = (ArrayReference)value;
      final Value keys = convertedMap.getValue(0);
      final Value values = convertedMap.getValue(1);
      if (keys instanceof ArrayReference && values instanceof ArrayReference) {
        return resolveDirectTrace((ArrayReference)keys, (ArrayReference)values, order);
      }

      throw new UnexpectedValueException("keys and values arrays must be arrays");
    }

    throw new UnexpectedValueException("value must be an array reference");
  }

  @NotNull
  private static Map<TraceElement, List<TraceElement>> resolveDirectTrace(@NotNull ArrayReference keys,
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

  @NotNull
  private static Map<TraceElement, List<TraceElement>> resolveReverse(@NotNull Value value,
                                                                      @NotNull TraceInfo order) {
    if (value instanceof ArrayReference) {
      final Value keys = ((ArrayReference)value).getValue(0);
      final Value values = ((ArrayReference)value).getValue(1);
      if (keys instanceof ArrayReference && values instanceof ArrayReference) {
        return resolveReverseTrace((ArrayReference)keys, (ArrayReference)values, order);
      }

      throw new UnexpectedValueException("keys and values arrays must be arrays");
    }

    throw new UnexpectedValueException("value must be an array reference");
  }

  private static Map<TraceElement, List<TraceElement>> resolveReverseTrace(@NotNull ArrayReference keys,
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
      result.computeIfAbsent(beforeElement, x -> new ArrayList<>()).add(afterElement);
    }

    return result;
  }

  private static class MyDistinctInfo extends ValuesOrderInfo {
    private final Map<TraceElement, List<TraceElement>> myDirectTrace;
    private final Map<TraceElement, List<TraceElement>> myReverseTrace;

    private MyDistinctInfo(@NotNull TraceInfo info,
                           @NotNull Map<TraceElement, List<TraceElement>> directTrace,
                           @NotNull Map<TraceElement, List<TraceElement>> reverseTrace) {
      super(info.getCall(), info.getValuesOrderBefore(), info.getValuesOrderAfter());
      myDirectTrace = directTrace;
      myReverseTrace = reverseTrace;
    }

    @Nullable
    @Override
    public Map<TraceElement, List<TraceElement>> getDirectTrace() {
      return Collections.unmodifiableMap(myDirectTrace);
    }

    @Nullable
    @Override
    public Map<TraceElement, List<TraceElement>> getReverseTrace() {
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
