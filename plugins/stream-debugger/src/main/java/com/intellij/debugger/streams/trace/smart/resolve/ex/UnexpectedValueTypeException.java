package com.intellij.debugger.streams.trace.smart.resolve.ex;

import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public class UnexpectedValueTypeException extends ResolveException {
  public UnexpectedValueTypeException(@NotNull String message) {
    super(message);
  }
}
