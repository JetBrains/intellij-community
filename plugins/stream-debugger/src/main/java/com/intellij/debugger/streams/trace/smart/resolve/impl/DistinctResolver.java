package com.intellij.debugger.streams.trace.smart.resolve.impl;

import com.intellij.debugger.streams.trace.smart.TraceElement;
import com.intellij.debugger.streams.trace.smart.resolve.TraceInfo;
import com.intellij.debugger.streams.trace.smart.resolve.TraceResolver;
import com.intellij.debugger.streams.trace.smart.resolve.ex.UnexpectedValueException;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Vitaliy.Bibaev
 */
public class DistinctResolver implements TraceResolver {
  private final TraceResolver myPeekResolver = new SimplePeekResolver();

  @NotNull
  @Override
  public TraceInfo resolve(@NotNull Value value) {
    if (value instanceof ArrayReference) {
      final Value peekTrace = ((ArrayReference)value).getValue(0);
      final Value distinctTrace = ((ArrayReference)value).getValue(1);
      final TraceInfo order = myPeekResolver.resolve(peekTrace);
      final Map<TraceElement, List<TraceElement>> direct = resolveDirect(distinctTrace);
      final Map<TraceElement, List<TraceElement>> reverse = resolveReverse(distinctTrace);
      return new MyDistinctInfo(order.getValuesOrder(), direct, reverse);
    }

    throw new UnexpectedValueException("distinct trace must be an array value");
  }

  private Map<TraceElement, List<TraceElement>> resolveDirect(@NotNull Value value) {
    // TODO
    return Collections.emptyMap();
  }

  private Map<TraceElement, List<TraceElement>> resolveReverse(@NotNull Value value) {
    // TODO
    return Collections.emptyMap();
  }

  private static class MyDistinctInfo extends ValuesOrderInfo {
    private final Map<TraceElement, List<TraceElement>> myDirectTrace;
    private final Map<TraceElement, List<TraceElement>> myReverseTrace;

    private MyDistinctInfo(@NotNull Map<Integer, TraceElement> order,
                           @NotNull Map<TraceElement, List<TraceElement>> directTrace,
                           @NotNull Map<TraceElement, List<TraceElement>> reverseTrace) {
      super(order);
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
}
