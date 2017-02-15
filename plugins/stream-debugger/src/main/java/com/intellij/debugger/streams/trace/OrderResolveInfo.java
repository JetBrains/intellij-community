package com.intellij.debugger.streams.trace;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * @author Vitaliy.Bibaev
 */
public interface OrderResolveInfo extends AdditionalTraceData {
  @NotNull
  List<TraceElement> items();
}
