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
  private final Map<Integer, TraceElement> myValuesOrderAfter;
  private final Map<Integer, TraceElement> myValuesOrderBefore;

  public ValuesOrderInfo(@NotNull Map<Integer, TraceElement> before, @NotNull Map<Integer, TraceElement> after) {
    myValuesOrderBefore = before;
    myValuesOrderAfter = after;
  }

  @NotNull
  @Override
  public Map<Integer, TraceElement> getValuesOrderBefore() {
    return myValuesOrderBefore;
  }

  @NotNull
  @Override
  public Map<Integer, TraceElement> getValuesOrderAfter() {
    return myValuesOrderAfter;
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
