package com.intellij.debugger.streams.trace.smart.handler;

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

  @NotNull
  static Variable of(@NotNull String type, @NotNull String name, @NotNull String initial) {
    return new VariableImpl(type, name, initial);
  }
}
