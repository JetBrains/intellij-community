package com.intellij.tools.apiDump.testData;

@SuppressWarnings("unused")
public enum JAbstractEnum {
  A {
    @Override
    public void publicAbstractMethod() {
    }

    @Override
    protected void protectedAbstractMethod() {
    }

    @Override
    void packagePrivateAbstractMethod() {
    }
  };

  public abstract void publicAbstractMethod();

  protected abstract void protectedAbstractMethod();

  abstract void packagePrivateAbstractMethod();
}
