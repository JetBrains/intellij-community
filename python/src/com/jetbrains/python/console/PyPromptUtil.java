package com.jetbrains.python.console;

import com.intellij.execution.console.LanguageConsoleImpl;

/**
 * @author traff
 */
public class PyPromptUtil {
  public static final String ORDINARY_PROMPT =      ">>> ";
  public static final String INPUT_PROMPT =      ">? ";
  public static final String INDENT_PROMPT =        "... ";
  static final String HELP_PROMPT =                 "help> ";
  public static final String EXECUTING_PROMPT =      "";
  static final String[] PROMPTS = new String[]{
    ORDINARY_PROMPT,
    INDENT_PROMPT,
    HELP_PROMPT
  };

  private PyPromptUtil() {
  }

  static String processPrompts(final LanguageConsoleImpl languageConsole, String string) {
    // Change prompt
    for (String prompt : PROMPTS) {
      if (string.startsWith(prompt)) {
        // Process multi prompts here
        if (prompt != HELP_PROMPT) {
          final StringBuilder builder = new StringBuilder();
          builder.append(prompt).append(prompt);
          while (string.startsWith(builder.toString())) {
            builder.append(prompt);
          }
          final String multiPrompt = builder.toString().substring(prompt.length());
          if (prompt == INDENT_PROMPT) {
            prompt = multiPrompt;
          }
          string = string.substring(multiPrompt.length());
        }
        else {
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
