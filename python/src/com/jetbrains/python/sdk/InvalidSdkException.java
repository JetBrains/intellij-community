package com.jetbrains.python.sdk;

public class InvalidSdkException extends Exception {
  public InvalidSdkException(String s) {
    super(s);
  }

  public InvalidSdkException(String message, Throwable cause) {
    super(message, cause);
  }
}
