package com.jetbrains.python.console;

import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.util.Key;

/**
 * @author oleg
 */
public class PyConsoleHighlightingUtil {
  public static final String ORDINARY_PROMPT =      ">>> ";
  public static final String INPUT_PROMPT =      ">? ";
  public static final String INDENT_PROMPT =        "... ";
  static final String HELP_PROMPT =                 "help> ";
  public static final String EXECUTING_PROMPT =      "";

  private PyConsoleHighlightingUtil() {
  }

  public static void printToConsoleView(final ConsoleView consoleView, String string, final Key attributes) {
    consoleView.print(string, outputTypeForAttributes(attributes));
  }

  public static ConsoleViewContentType outputTypeForAttributes(Key attributes) {
    final ConsoleViewContentType outputType;
    if (attributes == ProcessOutputTypes.STDERR) {
      outputType = ConsoleViewContentType.ERROR_OUTPUT;
    } else
    if (attributes == ProcessOutputTypes.SYSTEM) {
      outputType = ConsoleViewContentType.SYSTEM_OUTPUT;
    } else {
      outputType = ConsoleViewContentType.NORMAL_OUTPUT;
    }
    return outputType;
  }
}
