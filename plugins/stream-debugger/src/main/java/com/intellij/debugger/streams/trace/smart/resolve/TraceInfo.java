package com.intellij.debugger.streams.trace.smart.resolve;

import com.intellij.debugger.streams.trace.smart.TraceElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * @author Vitaliy.Bibaev
 */
public interface TraceInfo {
  @NotNull
  Map<Integer, TraceElement> getValuesOrderBefore();

  @NotNull
  Map<Integer, TraceElement> getValuesOrderAfter();

  @Nullable
  Map<TraceElement, List<TraceElement>> getDirectTrace();

  @Nullable
  Map<TraceElement, List<TraceElement>> getReverseTrace();
}
