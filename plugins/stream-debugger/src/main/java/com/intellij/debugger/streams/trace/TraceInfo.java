package com.intellij.debugger.streams.trace;

import com.intellij.debugger.streams.trace.TraceElement;
import com.intellij.debugger.streams.wrapper.StreamCall;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * @author Vitaliy.Bibaev
 */
public interface TraceInfo {
  @NotNull
  StreamCall getCall();

  @NotNull
  Map<Integer, TraceElement> getValuesOrderBefore();

  @NotNull
  Map<Integer, TraceElement> getValuesOrderAfter();

  @Nullable
  Map<TraceElement, List<TraceElement>> getDirectTrace();

  @Nullable
  Map<TraceElement, List<TraceElement>> getReverseTrace();
}
