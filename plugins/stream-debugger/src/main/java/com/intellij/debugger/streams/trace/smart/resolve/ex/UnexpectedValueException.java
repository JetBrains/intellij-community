package com.intellij.debugger.streams.trace.smart.resolve.ex;

import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public class UnexpectedValueException extends ResolveException {
  public UnexpectedValueException(@NotNull String s) {
    super(s);
  }

  public UnexpectedValueException(@NotNull String message, @NotNull Throwable cause) {
    super(message, cause);
  }
}
