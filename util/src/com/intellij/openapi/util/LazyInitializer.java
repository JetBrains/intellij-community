package com.intellij.openapi.util;

public abstract class LazyInitializer<Value, Exc extends Throwable> implements ThrowableComputable<Value, Exc> {
  private Value myValue;

  public Value compute() throws Exc {
    if (myValue == null) {
      myValue = compute();
    }
    return myValue;
  }

  protected abstract Value computeInternal() throws Exc;
}
