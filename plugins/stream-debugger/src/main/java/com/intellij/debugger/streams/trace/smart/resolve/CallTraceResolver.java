package com.intellij.debugger.streams.trace.smart.resolve;

import com.intellij.debugger.streams.wrapper.StreamCall;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public interface CallTraceResolver {
  @NotNull
  TraceInfo resolve(@NotNull StreamCall call, @NotNull Value value);
}
