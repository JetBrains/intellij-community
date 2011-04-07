package com.jetbrains.python.console;

import com.intellij.execution.console.LanguageConsoleImpl;
import com.intellij.execution.process.ColoredProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.util.Key;
import com.jetbrains.python.highlighting.PyHighlighter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author oleg
 */
public class PyConsoleHighlightingUtil {
  public static final String ORDINARY_PROMPT =      ">>> ";
  public static final String INPUT_PROMPT =      ">? ";
  public static final String INDENT_PROMPT =        "... ";
  static final String HELP_PROMPT =                 "help> ";
  public static final String EXECUTING_PROMPT =      "";

  public static final Key STRING_KEY = new Key("PYTHON_STRING");
  public static final Key NUMBER_KEY = new Key("PYTHON_NUMBER");

  static final String NUMBERS = "((0[xX][0-9a-fA-F]+)|(\\d+(_\\d+)*(\\.\\d+)?))";
  static final String STRINGS = "((\"[^\"\n]*\")|((?<!\\w)'[^'\n]*'))";

  static final Pattern CODE_ELEMENT_PATTERN = Pattern.compile(NUMBERS + "|" + STRINGS);

  static final ConsoleViewContentType STRING_ATTRIBUTES = new ConsoleViewContentType(STRING_KEY.toString(),
                             ColoredProcessHandler.getByKey(PyHighlighter.PY_STRING));
  static final ConsoleViewContentType NUMBER_ATTRIBUTES = new ConsoleViewContentType(NUMBER_KEY.toString(),
                                                        ColoredProcessHandler.getByKey(PyHighlighter.PY_NUMBER));

  private PyConsoleHighlightingUtil() {
  }

  public static void processOutput(final LanguageConsoleImpl console, String string, final Key attributes) {
    final ConsoleViewContentType outputType;
    if (attributes == ProcessOutputTypes.STDERR) {
      outputType = ConsoleViewContentType.ERROR_OUTPUT;
    } else
    if (attributes == ProcessOutputTypes.SYSTEM) {
      outputType = ConsoleViewContentType.SYSTEM_OUTPUT;
    } else {
      outputType = ConsoleViewContentType.NORMAL_OUTPUT;
    }
    // Highlight output by pattern
    Matcher matcher;
    while ((matcher = CODE_ELEMENT_PATTERN.matcher(string)).find()) {
      LanguageConsoleImpl.printToConsole(console, string.substring(0, matcher.start()), outputType, null);
      // Number group
      if (matcher.group(1) != null) {
        LanguageConsoleImpl.printToConsole(console, matcher.group(1), outputType, NUMBER_ATTRIBUTES);
      }
      // String group
      else if (matcher.group(6) != null) {
        LanguageConsoleImpl.printToConsole(console, matcher.group(6), outputType, STRING_ATTRIBUTES);
      }
      else {
        LanguageConsoleImpl.printToConsole(console, matcher.group(), outputType, null);
      }
      string = string.substring(matcher.end());
    }
    LanguageConsoleImpl.printToConsole(console, string, outputType, null);
  }
}
