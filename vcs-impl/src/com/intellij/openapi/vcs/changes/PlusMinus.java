package com.intellij.openapi.vcs.changes;

public interface PlusMinus<T> {
  void plus(final T t);
  void minus(final T t);
}
