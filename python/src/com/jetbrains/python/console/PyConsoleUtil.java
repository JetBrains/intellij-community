package com.jetbrains.python.console;

import com.intellij.execution.console.LanguageConsoleImpl;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
public class PyConsoleUtil {
  public static final String ORDINARY_PROMPT = ">>> ";
  public static final String INPUT_PROMPT = ">? ";
  public static final String INDENT_PROMPT = "... ";
  static final String HELP_PROMPT = "help> ";
  public static final String EXECUTING_PROMPT = "";

  private static final String IPYTHON_PAGING_PROMPT = "---Return to continue, q to quit---";

  static final String[] PROMPTS = new String[]{
    ORDINARY_PROMPT,
    INDENT_PROMPT,
    HELP_PROMPT,
    IPYTHON_PAGING_PROMPT
  };
  public static final String DOUBLE_QUOTE_MULTILINE = "\"\"\"";
  public static final String SINGLE_QUOTE_MULTILINE = "'''";
  static final Key<Boolean> IPYTHON = Key.create("ipython");

  private PyConsoleUtil() {
  }

  public static boolean isPagingPrompt(@NotNull String prompt) {
    return IPYTHON_PAGING_PROMPT.equals(prompt.trim());
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
          scrollDown(languageConsole.getCurrentEditor());
        }
        break;
      }
    }
    return string;
  }

  public static boolean isMultilineStarts(String line, String substring) {
    return StringUtils.countMatches(line, substring) % 2 == 1;
  }

  public static void scrollDown(final Editor currentEditor) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        currentEditor.getCaretModel().moveToOffset(currentEditor.getDocument().getTextLength());
      }
    });
  }

  public static boolean isSingleQuoteMultilineStarts(String line) {
    return isMultilineStarts(line, SINGLE_QUOTE_MULTILINE);
  }

  public static boolean isDoubleQuoteMultilineStarts(String line) {
    return isMultilineStarts(line, DOUBLE_QUOTE_MULTILINE);
  }

  public static boolean detectIPythonImported(@NotNull String text, final ConsoleViewContentType outputType) {
    return text.contains("PyDev console: using IPython 0.1") && outputType == ConsoleViewContentType.ERROR_OUTPUT;
  }

  public static boolean detectSourcePrinting(@NotNull String text) {
    return text.contains("Source:");
  }

  public static boolean detectIPythonStart(@NotNull String text) {
    return text.contains("IPython-->");
  }

  public static boolean detectIPythonEnd(@NotNull String text) {
    return text.contains("<--IPython");
  }
  
  public static boolean isIPythonDetected(VirtualFile file) {
    if (file == null) {
      return false;
    }
    Boolean detected = file.getUserData(IPYTHON);
    return detected != null && detected.booleanValue();
  }
}
