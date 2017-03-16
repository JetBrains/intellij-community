package com.intellij.debugger.streams.trace.impl.resolve.ex;

import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public class UnexpectedArrayLengthException extends ResolveException {
  public UnexpectedArrayLengthException(@NotNull String message) {
    super(message);
  }
}
