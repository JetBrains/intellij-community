package com.jetbrains.python.console;

import com.intellij.execution.console.LanguageConsoleImpl;
import com.intellij.execution.process.ColoredProcessHandler;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.python.run.PythonProcessHandler;

import java.nio.charset.Charset;

/**
 * @author oleg
 */
public class PyConsoleProcessHandler extends PythonProcessHandler {
  private final LanguageConsoleImpl myLanguageConsole;

  public PyConsoleProcessHandler(final Process process,
                                 final LanguageConsoleImpl languageConsole,
                                 final String commandLine,
                                 final Charset charset) {
    super(process, commandLine, charset);
    myLanguageConsole = languageConsole;
  }


  private final String[] PROMPTS = new String[]{
    PyConsoleHighlightingUtil.ORDINARY_PROMPT,
    PyConsoleHighlightingUtil.INDENT_PROMPT,
    PyConsoleHighlightingUtil.HELP_PROMPT
  };

  @Override
  protected void textAvailable(final String text, final Key attributes) {
    final String string = processPrompts(myLanguageConsole, StringUtil.convertLineSeparators(text));
    PyConsoleHighlightingUtil.processOutput(myLanguageConsole, string, attributes);
    // scroll to end
    myLanguageConsole.queueUiUpdate(true);
  }

  private String processPrompts(final LanguageConsoleImpl languageConsole, String string) {
    // Change prompt
    for (String prompt : PROMPTS) {
      if (string.startsWith(prompt)) {
        // Process multi prompts here
        if (prompt != PyConsoleHighlightingUtil.HELP_PROMPT){
          final StringBuilder builder = new StringBuilder();
          builder.append(prompt).append(prompt);
          while (string.startsWith(builder.toString())){
            builder.append(prompt);
          }
          final String multiPrompt = builder.toString().substring(prompt.length());
          if (prompt == PyConsoleHighlightingUtil.INDENT_PROMPT){
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
}
