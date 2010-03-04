package com.jetbrains.python.console;

import com.intellij.execution.process.ColoredProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.util.Key;

import java.nio.charset.Charset;

/**
 * @author oleg
 */
class PyConsoleProcessHandler extends ColoredProcessHandler {
  private PyConsoleRunner myPyConsoleRunner;

  public PyConsoleProcessHandler(final PyConsoleRunner pyConsoleRunner,
                                 final Process process,
                                 final String commandLine,
                                 final Charset charset) {
    super(process, commandLine, charset);
    myPyConsoleRunner = pyConsoleRunner;
  }

  @Override
  protected void textAvailable(String text, final Key attributes) {
    for (String prompt : PROMPTS) {
      if (text.startsWith(prompt)) {
        final String currentPrompt = myPyConsoleRunner.getLanguageConsole().getPrompt();
        if (!currentPrompt.equals(prompt)) {
          myPyConsoleRunner.getLanguageConsole().setPrompt(prompt);
        }
        return;
      }
    }
    super.textAvailable(text, ProcessOutputTypes.STDOUT);
  }

  private final String[] PROMPTS = new String[]{">>>", "help>"};
}
