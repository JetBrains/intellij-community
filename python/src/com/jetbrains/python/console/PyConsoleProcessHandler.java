package com.jetbrains.python.console;

import com.intellij.execution.console.LanguageConsoleImpl;
import com.intellij.execution.process.ColoredProcessHandler;
import com.intellij.execution.process.ConsoleHighlighter;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.python.PyHighlighter;

import javax.swing.plaf.TextUI;
import java.nio.charset.Charset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author oleg
 */
class PyConsoleProcessHandler extends OSProcessHandler {
  private PyConsoleRunner myPyConsoleRunner;
  private final Charset myCharset;

  public PyConsoleProcessHandler(final PyConsoleRunner pyConsoleRunner,
                                 final Process process,
                                 final String commandLine,
                                 final Charset charset) {
    super(process, commandLine);
    myPyConsoleRunner = pyConsoleRunner;
    myCharset = charset;
  }

  private final String[] PROMPTS = new String[]{">>>", "help>"};

  public static final Key STRING_KEY = new Key("PYTHON_STRING");
  public static final Key NUMBER_KEY = new Key("PYTHON_NUMBER");

  private static String NUMBERS = "(?<![\\w_#$=])(\\d+(_\\d+)*(\\.\\d+)?)";
  private static String STRINGS = "((\"[^\"\n]*\")|((?<!\\w)'[^'\n]*'))";

  private static Pattern CODE_ELEMENT_PATTERN = Pattern.compile(NUMBERS + "|" + STRINGS);

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
    final LanguageConsoleImpl languageConsole = myPyConsoleRunner.getLanguageConsole();
    String string = text;
    // Change prompt
    for (String prompt : PROMPTS) {
      if (string.startsWith(prompt)) {
        final String currentPrompt = languageConsole.getPrompt();
        if (!currentPrompt.equals(prompt)) {
          languageConsole.setPrompt(prompt);
        }
        string = string.substring(prompt.length());
        break;
      }
    }

    final Ref<Boolean> shouldScroll = new Ref<Boolean>(false);
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        shouldScroll.set(languageConsole.shouldScrollHistoryToEnd());
      }
    });
    Matcher matcher;
    while ((matcher = CODE_ELEMENT_PATTERN.matcher(string)).find()) {
      printToConsole(languageConsole, string.substring(0, matcher.start()), ConsoleViewContentType.NORMAL_OUTPUT);
      if (matcher.group(1) != null) {
        printToConsole(languageConsole, matcher.group(1), NUMBER_ATTRIBUTES);
      } else if (matcher.group(4) != null) {
        printToConsole(languageConsole, matcher.group(4), STRING_ATTRIBUTES);
      } else {
        printToConsole(languageConsole, matcher.group(), ConsoleViewContentType.NORMAL_OUTPUT);
      }
      string = string.substring(matcher.end());
    }
    printToConsole(languageConsole, string, ConsoleViewContentType.NORMAL_OUTPUT);

    // Revert scrolling
    if (shouldScroll.get()){
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          languageConsole.queueUiUpdate(false);
        }
      });
    }
  }

  private static void printToConsole(final LanguageConsoleImpl console, final String string, final ConsoleViewContentType type) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        final TextAttributes attributes = TextAttributes.merge(type.getAttributes(), ConsoleHighlighter.OUT.getDefaultAttributes());
        console.addToHistory(string, attributes);
      }
    });
  }
}
