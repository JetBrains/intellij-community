package com.intellij.debugger.streams.trace.smart.resolve.impl;

import com.intellij.debugger.streams.trace.smart.TraceElement;
import com.intellij.debugger.streams.trace.smart.resolve.TraceInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * @author Vitaliy.Bibaev
 */
public class ValuesOrderInfo implements TraceInfo {
  private final Map<Integer, TraceElement> myValuesOrder;

  public ValuesOrderInfo(@NotNull Map<Integer, TraceElement> valuesOrder) {
    myValuesOrder = valuesOrder;
  }

  @NotNull
  @Override
  public Map<Integer, TraceElement> getValuesOrder() {
    return myValuesOrder;
  }

  @Nullable
  @Override
  public Map<TraceElement, List<TraceElement>> getDirectTrace() {
    return null;
  }

  @Nullable
  @Override
  public Map<TraceElement, List<TraceElement>> getReverseTrace() {
    return null;
  }
}
