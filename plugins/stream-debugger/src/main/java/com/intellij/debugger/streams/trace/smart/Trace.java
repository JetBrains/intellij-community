package com.intellij.debugger.streams.trace.smart;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public interface Trace {
  @NotNull
  List<TraceElement> getTrace();

  @Nullable
  AdditionalTraceData getAdditionalTraceData();
}
