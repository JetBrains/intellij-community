// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger;


import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NonNls;

public class PyDebuggerException extends Exception {

  public PyDebuggerException(@NonNls String message) {
    super(message);
  }

  public PyDebuggerException(@NonNls String message, Throwable cause) {
    super(message, cause);
  }

  public @NlsSafe String getTracebackError() {
    @NonNls String text = getMessage();
    if (text != null && text.contains("Traceback (most recent call last):")) {
      final String[] lines = text.split("\n");
      if (lines.length > 0) {
        text = lines[lines.length - 1];
      }
    }
    return text;
  }
}
