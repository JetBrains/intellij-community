package com.intellij.debugger.streams.trace;

import com.intellij.debugger.streams.wrapper.StreamChain;
import org.jetbrains.annotations.NotNull;


/**
 * @author Vitaliy.Bibaev
 */
public interface TraceExpressionBuilder {
  @NotNull
  String createTraceExpression(@NotNull StreamChain chain);
}
