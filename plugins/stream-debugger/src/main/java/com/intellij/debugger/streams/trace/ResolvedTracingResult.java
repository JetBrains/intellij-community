package com.intellij.debugger.streams.trace;

import com.intellij.debugger.streams.resolve.ResolvedTrace;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public interface ResolvedTracingResult {
  @NotNull
  List<ResolvedTrace> getResolvedCalls();

  @Nullable
  Value getResult();
}
