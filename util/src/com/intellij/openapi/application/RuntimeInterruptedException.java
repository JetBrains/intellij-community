package com.intellij.openapi.application;

public class RuntimeInterruptedException extends RuntimeException {
  public RuntimeInterruptedException(InterruptedException cause) {
    super(cause);
  }
}