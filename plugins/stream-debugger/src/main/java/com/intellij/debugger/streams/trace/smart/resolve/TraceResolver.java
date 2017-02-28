package com.intellij.debugger.streams.trace.smart.resolve;

import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public interface TraceResolver {
  @NotNull
  TraceInfo resolve(@NotNull Value value);
}
