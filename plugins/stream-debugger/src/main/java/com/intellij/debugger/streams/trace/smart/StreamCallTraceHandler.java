package com.intellij.debugger.streams.trace.smart;

import com.intellij.debugger.streams.remote.InvokeMethodProxy;
import com.intellij.debugger.streams.wrapper.MethodCall;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public interface StreamCallTraceHandler {
  @NotNull
  List<MethodCall> callsBefore(int callNumber);

  @NotNull
  List<MethodCall> callsAfter(int callNumber);

  @NotNull
  TraceElement resolveTrace(@NotNull InvokeMethodProxy object );
}
