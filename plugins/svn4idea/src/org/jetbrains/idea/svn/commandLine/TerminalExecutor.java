// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.commandLine;

import com.intellij.execution.CommandLineUtil;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.PtyCommandLine;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class TerminalExecutor extends CommandExecutor {

  private final List<InteractiveCommandListener> myInteractiveListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  public TerminalExecutor(@NotNull @NonNls String exePath, @NotNull Command command) {
    super(exePath, command);
  }

  public void addInteractiveListener(@NotNull InteractiveCommandListener listener) {
    myInteractiveListeners.add(listener);
  }

  @Override
  public Boolean wasError() {
    return Boolean.FALSE;
  }

  @Override
  protected void startHandlingStreams() {
    for (InteractiveCommandListener listener : myInteractiveListeners) {
      ((TerminalProcessHandler)myHandler).addInteractiveListener(listener);
    }

    super.startHandlingStreams();
  }

  @Override
  protected @NotNull SvnProcessHandler createProcessHandler() {
    return new TerminalProcessHandler(myProcess, myCommandLine, needsUtf8Output(), false);
  }

  /**
   * TODO: remove this when separate streams for output and errors are implemented for Unix.
   */
  @Override
  public @NotNull ByteArrayOutputStream getBinaryOutput() {
    if (this instanceof WinTerminalExecutor) {
      return super.getBinaryOutput();
    }

    ByteArrayOutputStream result = new ByteArrayOutputStream();
    byte[] outputBytes = getOutput().getBytes(StandardCharsets.UTF_8);

    result.write(outputBytes, 0, outputBytes.length);

    return result;
  }

  @Override
  protected @NotNull GeneralCommandLine createCommandLine() {
    return new PtyCommandLine();
  }

  @Override
  protected @NotNull Process createProcess() throws ExecutionException {
    List<String> parameters = escapeArguments(buildParameters());

    return createProcess(parameters);
  }

  protected @NotNull List<String> buildParameters() {
    return CommandLineUtil.toCommandLine(myCommandLine.getExePath(), myCommandLine.getParametersList().getList());
  }

  protected @NotNull Process createProcess(@NotNull List<String> parameters) throws ExecutionException {
    try {
      return ((PtyCommandLine)myCommandLine).withConsoleMode(false).startProcessWithPty(parameters);
    }
    catch (IOException e) {
      throw new ExecutionException(e);
    }
  }

  @Override
  public void logCommand() {
    super.logCommand();

    LOG.info("Terminal output " + ((TerminalProcessHandler)myHandler).getTerminalOutput());
  }

  protected @NotNull List<String> escapeArguments(@NotNull List<String> arguments) {
    return arguments;
  }
}
