package com.jetbrains.python.console;

import com.intellij.execution.console.LanguageConsoleImpl;
import com.intellij.execution.process.ColoredProcessHandler;
import com.intellij.execution.process.ConsoleHighlighter;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.python.PyHighlighter;

import java.nio.charset.Charset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author oleg
 */
public class PyConsoleProcessHandler extends OSProcessHandler {
  private final Charset myCharset;
  private final LanguageConsoleImpl myLanguageConsole;

  public PyConsoleProcessHandler(final Process process,
                                 final LanguageConsoleImpl languageConsole,
                                 final String commandLine,
                                 final Charset charset) {
    super(process, commandLine);
    myLanguageConsole = languageConsole;
    myCharset = charset;
  }

  public static final String ORDINARY_PROMPT =  ">>> ";
  private static final String INDENT_PROMPT =   "... ";
  private static final String HELP_PROMPT =     "help> ";


  private final String[] PROMPTS = new String[]{ORDINARY_PROMPT, INDENT_PROMPT, HELP_PROMPT};

  public static final Key STRING_KEY = new Key("PYTHON_STRING");
  public static final Key NUMBER_KEY = new Key("PYTHON_NUMBER");

  private static final String NUMBERS = "((0[xX][0-9a-fA-F]+)|(\\d+(_\\d+)*(\\.\\d+)?))";
  private static final String STRINGS = "((\"[^\"\n]*\")|((?<!\\w)'[^'\n]*'))";

  private static final Pattern CODE_ELEMENT_PATTERN = Pattern.compile(NUMBERS + "|" + STRINGS);

  @Override
  public Charset getCharset() {
    return myCharset != null ? myCharset : super.getCharset();
  }

  private static final ConsoleViewContentType STRING_ATTRIBUTES = new ConsoleViewContentType(STRING_KEY.toString(),
                             ColoredProcessHandler.getByKey(PyHighlighter.PY_STRING));

  private static final ConsoleViewContentType NUMBER_ATTRIBUTES = new ConsoleViewContentType(NUMBER_KEY.toString(),
                             ColoredProcessHandler.getByKey(PyHighlighter.PY_NUMBER));

  @Override
  public void notifyTextAvailable(final String text, final Key attributes) {
    String string = processPrompts(myLanguageConsole, StringUtil.convertLineSeparators(text));
    processOutput(myLanguageConsole, string, attributes);
  }

  public static void processOutput(LanguageConsoleImpl console, String string, final Key attributes) {
    final ConsoleViewContentType type =
      attributes == ProcessOutputTypes.STDERR ? ConsoleViewContentType.ERROR_OUTPUT : ConsoleViewContentType.NORMAL_OUTPUT;
    // Highlight output by pattern
    Matcher matcher;
    while ((matcher = CODE_ELEMENT_PATTERN.matcher(string)).find()) {
      printToConsole(console, string.substring(0, matcher.start()), type);
      // Number group
      if (matcher.group(1) != null) {
        printToConsole(console, matcher.group(1), NUMBER_ATTRIBUTES);
      }
      // String group
      else if (matcher.group(6) != null) {
        printToConsole(console, matcher.group(6), STRING_ATTRIBUTES);
      }
      else {
        printToConsole(console, matcher.group(), type);
      }
      string = string.substring(matcher.end());
    }
    printToConsole(console, string, type);
  }

  private String processPrompts(final LanguageConsoleImpl languageConsole, String string) {
    // Change prompt
    for (String prompt : PROMPTS) {
      if (string.startsWith(prompt)) {
        // Process multi prompts here
        if (prompt != HELP_PROMPT){
          final StringBuilder builder = new StringBuilder();
          builder.append(prompt).append(prompt);
          while (string.startsWith(builder.toString())){
            builder.append(prompt);
          }
          final String multiPrompt = builder.toString().substring(prompt.length());
          if (prompt == INDENT_PROMPT){
            prompt = multiPrompt;
          }
          string = string.substring(multiPrompt.length());
        } else {
          string = string.substring(prompt.length());
        }

        // Change console editor prompt if required
        final String currentPrompt = languageConsole.getPrompt();
        final String trimmedPrompt = prompt.trim();
        if (!currentPrompt.equals(trimmedPrompt)) {
          languageConsole.setPrompt(trimmedPrompt);
        }
        break;
      }
    }
    return string;
  }

  private static void printToConsole(final LanguageConsoleImpl console, final String string, final ConsoleViewContentType type) {
    final TextAttributes attributes = TextAttributes.merge(type.getAttributes(), ConsoleHighlighter.OUT.getDefaultAttributes());
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        console.printToHistory(string, attributes);
      }
    }, ModalityState.stateForComponent(console.getComponent()));
  }
}
