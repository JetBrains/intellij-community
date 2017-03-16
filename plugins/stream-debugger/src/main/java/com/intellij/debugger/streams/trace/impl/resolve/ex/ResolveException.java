package com.intellij.debugger.streams.trace.impl.resolve.ex;

import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
class ResolveException extends IllegalStateException {
  ResolveException(@NotNull String s) {
    super(s);
  }

  ResolveException(@NotNull String message, @NotNull Throwable cause) {
    super(message, cause);
  }
}
