package com.intellij.debugger.streams.trace.smart.resolve.impl;

import com.intellij.debugger.streams.trace.smart.resolve.TraceInfo;
import com.intellij.debugger.streams.trace.smart.resolve.TraceResolver;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public class SimplePeekResolver implements TraceResolver {
  @NotNull
  @Override
  public TraceInfo resolve(@NotNull Value value) {
    return null;
  }
}
