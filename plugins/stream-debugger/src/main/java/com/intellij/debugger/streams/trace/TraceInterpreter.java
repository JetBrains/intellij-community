package com.intellij.debugger.streams.trace;

import com.intellij.debugger.streams.wrapper.StreamChain;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public interface TraceInterpreter {
  @NotNull
  TracingResult interpret(@NotNull StreamChain chain, @NotNull Value evaluationResult);
}
