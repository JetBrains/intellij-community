package com.intellij.util;

public interface ThrowableConsumer<S, T extends Throwable> {
  void consume(final S s) throws T;
}
