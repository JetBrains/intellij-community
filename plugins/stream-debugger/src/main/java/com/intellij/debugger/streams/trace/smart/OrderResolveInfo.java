package com.intellij.debugger.streams.trace.smart;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public interface OrderResolveInfo extends AdditionalTraceData {
  @NotNull
  List<TraceElement> items();
}
