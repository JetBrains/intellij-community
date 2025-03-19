// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.commandLine;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnUtil;

import java.util.List;
import java.util.regex.Matcher;

public class TerminalProcessHandler extends SvnProcessHandler {

  private final List<InteractiveCommandListener> myInteractiveListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final CapturingProcessAdapter terminalOutputCapturer = new CapturingProcessAdapter();

  private final StringBuilder outputLine = new StringBuilder();
  private final StringBuilder errorLine = new StringBuilder();

  public TerminalProcessHandler(@NotNull Process process, @NotNull GeneralCommandLine commandLine, boolean forceUtf8, boolean forceBinary) {
    super(process, commandLine, forceUtf8, forceBinary);
    setHasPty(true);
    setShouldDestroyProcessRecursively(false);
  }

  public void addInteractiveListener(@NotNull InteractiveCommandListener listener) {
    myInteractiveListeners.add(listener);
  }

  @Override
  protected boolean processHasSeparateErrorStream() {
    return false;
  }

  @Override
  public void notifyTextAvailable(@NotNull String text, @NotNull Key outputType) {
    if (ProcessOutputTypes.SYSTEM.equals(outputType)) {
      super.notifyTextAvailable(text, outputType);
    }
    else {
      terminalOutputCapturer.onTextAvailable(new ProcessEvent(this, text), outputType);

      text = filterText(text);

      if (!StringUtil.isEmpty(text)) {
        StringBuilder lastLine = getLastLineFor(outputType);
        String currentLine = lastLine.append(text).toString();
        lastLine.setLength(0);

        currentLine = filterCombinedText(currentLine);

        // check if current line presents some interactive output
        boolean handled = handlePrompt(currentLine, outputType);
        if (!handled) {
          notify(currentLine, outputType, lastLine);
        }
      }
    }
  }

  protected boolean handlePrompt(String text, Key outputType) {
    // if process has separate output and error streams => try to handle prompts only from error stream output
    boolean shouldHandleWithListeners = !processHasSeparateErrorStream() || ProcessOutputTypes.STDERR.equals(outputType);

    return shouldHandleWithListeners && handlePromptWithListeners(text, outputType);
  }

  private boolean handlePromptWithListeners(String text, Key outputType) {
    boolean result = false;

    for (InteractiveCommandListener listener : myInteractiveListeners) {
      result |= listener.handlePrompt(text, outputType);
    }

    return result;
  }

  protected @NotNull String filterCombinedText(@NotNull String currentLine) {
    return currentLine;
  }

  protected @NotNull String filterText(@NotNull String text) {
    return text;
  }

  private void notify(@NotNull String text, @NotNull Key outputType, @NotNull StringBuilder lastLine) {
    // text is not more than one line - either one line or part of the line
    if (StringUtil.endsWith(text, "\n")) {
      // we have full line - notify listeners
      super.notifyTextAvailable(text, resolveOutputType(text, outputType));
    }
    else {
      // save line part to lastLine
      lastLine.append(text);
    }
  }

  protected @NotNull Key resolveOutputType(@NotNull String line, @NotNull Key outputType) {
    Key result = outputType;

    if (!ProcessOutputTypes.SYSTEM.equals(outputType)) {
      Matcher errorMatcher = SvnUtil.ERROR_PATTERN.matcher(line);
      Matcher warningMatcher = SvnUtil.WARNING_PATTERN.matcher(line);

      result = errorMatcher.find() || warningMatcher.find() ? ProcessOutputTypes.STDERR : ProcessOutputTypes.STDOUT;
    }

    return result;
  }

  private @NotNull StringBuilder getLastLineFor(Key outputType) {
    if (ProcessOutputTypes.STDERR.equals(outputType)) {
      return errorLine;
    }
    else if (ProcessOutputTypes.STDOUT.equals(outputType)) {
      return outputLine;
    }
    else {
      throw new IllegalArgumentException("Unknown process output type " + outputType);
    }
  }

  public String getTerminalOutput() {
    return terminalOutputCapturer.getOutput().getStdout();
  }
}
