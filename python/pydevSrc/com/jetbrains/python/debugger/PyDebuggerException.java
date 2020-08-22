package com.jetbrains.python.debugger;


import com.intellij.openapi.util.NlsContexts.DialogMessage;

public class PyDebuggerException extends Exception {

  public PyDebuggerException(@DialogMessage String message) {
    super(message);
  }

  public PyDebuggerException(@DialogMessage String message, Throwable cause) {
    super(message, cause);
  }

  public @DialogMessage String getTracebackError() {
    @DialogMessage String text = getMessage();
    if (text != null && text.contains("Traceback (most recent call last):")) {
      final String[] lines = text.split("\n");
      if (lines.length > 0) {
        text = lines[lines.length - 1];
      }
    }
    return text;
  }

}
