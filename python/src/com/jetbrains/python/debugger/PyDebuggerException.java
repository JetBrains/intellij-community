package com.jetbrains.python.debugger;


public class PyDebuggerException extends Exception {

  public PyDebuggerException(String message) {
    super(message);
  }

  public PyDebuggerException(String message, Throwable cause) {
    super(message, cause);
  }

}
