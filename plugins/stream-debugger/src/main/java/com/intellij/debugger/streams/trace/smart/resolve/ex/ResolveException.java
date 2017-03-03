package com.intellij.debugger.streams.trace.smart.resolve.ex;

import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public class ResolveException extends IllegalStateException {
  public ResolveException(@NotNull String s) {
    super(s);
  }

  public ResolveException(@NotNull String message, @NotNull Throwable cause) {
    super(message, cause);
  }
}
