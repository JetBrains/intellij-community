package com.jetbrains.python.remote;

/**
 * @author traff
 */
public class PyRemoteInterpreterException extends Exception {
  public PyRemoteInterpreterException() {
  }

  public PyRemoteInterpreterException(String s) {
    super(s);
  }

  public PyRemoteInterpreterException(String s, Throwable throwable) {
    super(s, throwable);
  }
}
