package com.intellij.debugger.streams.trace.impl.handler;

import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public interface Variable {
  @NotNull
  String getName();

  @NotNull
  String getTypeName();

  @NotNull
  String getInitialExpression();
}
