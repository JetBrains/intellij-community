package com.intellij.debugger.streams.trace.impl.resolve.ex;

import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public class UnexpectedValueTypeException extends ResolveException {
  public UnexpectedValueTypeException(@NotNull String message) {
    super(message);
  }
}
