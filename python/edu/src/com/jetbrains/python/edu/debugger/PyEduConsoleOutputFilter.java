package com.jetbrains.python.edu.debugger;

import com.intellij.execution.ui.ConsoleViewContentType;
import com.jetbrains.python.console.PyConsoleOutputFilter;
import org.jetbrains.annotations.NotNull;

public class PyEduConsoleOutputFilter implements PyConsoleOutputFilter {
  @Override
  public boolean reject(@NotNull String text, @NotNull ConsoleViewContentType outputType) {
    if (outputType.equals(ConsoleViewContentType.SYSTEM_OUTPUT) && !text.contains("exit code")) {
      return true;
    }
    if (text.startsWith("pydev debugger")) {
      return true;
    }
    return false;
  }
}
