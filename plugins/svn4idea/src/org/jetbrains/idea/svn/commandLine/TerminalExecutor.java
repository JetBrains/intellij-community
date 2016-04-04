/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.svn.commandLine;

import com.intellij.execution.CommandLineUtil;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.PtyCommandLine;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * @author Konstantin Kolosovsky.
 */
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

  @NotNull
  @Override
  protected SvnProcessHandler createProcessHandler() {
    return new TerminalProcessHandler(myProcess, myCommandLine.getCommandLineString(), needsUtf8Output(), false);
  }

  /**
   * TODO: remove this when separate streams for output and errors are implemented for Unix.
   */
  @NotNull
  @Override
  public ByteArrayOutputStream getBinaryOutput() {
    if (this instanceof WinTerminalExecutor) {
      return super.getBinaryOutput();
    }

    ByteArrayOutputStream result = new ByteArrayOutputStream();
    byte[] outputBytes = CharsetToolkit.getUtf8Bytes(getOutput());

    result.write(outputBytes, 0, outputBytes.length);

    return result;
  }

  @NotNull
  @Override
  protected GeneralCommandLine createCommandLine() {
    return new PtyCommandLine();
  }

  @NotNull
  @Override
  protected Process createProcess() throws ExecutionException {
    List<String> parameters = escapeArguments(buildParameters());

    return createProcess(parameters);
  }

  @NotNull
  protected List<String> buildParameters() {
    return CommandLineUtil.toCommandLine(myCommandLine.getExePath(), myCommandLine.getParametersList().getList());
  }

  @NotNull
  protected Process createProcess(@NotNull List<String> parameters) throws ExecutionException {
    try {
      return ((PtyCommandLine)myCommandLine).startProcessWithPty(parameters, false);
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

  @NotNull
  protected List<String> escapeArguments(@NotNull List<String> arguments) {
    return arguments;
  }
}
