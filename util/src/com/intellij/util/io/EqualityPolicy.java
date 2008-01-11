package com.intellij.util.io;

public interface EqualityPolicy<T> {
  int getHashCode(T value);

  boolean isEqual(T val1, T val2);
}
