/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.pty4j.PtyProcess;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;

/**
 * @author Konstantin Kolosovsky.
 */
public class TerminalExecutor extends CommandExecutor {

  // max available value is 480
  // if greater value is provided than the default value of 80 will be assumed
  // this could provide unnecessary line breaks and thus could break parsing logic
  private static final int TERMINAL_WINDOW_MAX_COLUMNS = 480;

  static {
    if (SystemInfo.isWindows) {
      System.setProperty("win.pty.cols", String.valueOf(TERMINAL_WINDOW_MAX_COLUMNS));
    }
  }

  private final List<InteractiveCommandListener> myInteractiveListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  public TerminalExecutor(@NotNull @NonNls String exePath, @NotNull Command command) {
    super(exePath, command);
  }

  public void addInteractiveListener(@NotNull InteractiveCommandListener listener) {
    myInteractiveListeners.add(listener);
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
  protected OSProcessHandler createProcessHandler() {
    return new TerminalProcessHandler(myProcess);
  }

  @NotNull
  @Override
  protected Process createProcess() throws ExecutionException {
    List<String> parameters =
      escapeArguments(CommandLineUtil.toCommandLine(myCommandLine.getExePath(), myCommandLine.getParametersList().getList()));

    try {
      return PtyProcess
        .exec(ArrayUtil.toStringArray(parameters), myCommandLine.getEnvironment(), myCommandLine.getWorkDirectory().getAbsolutePath());
    }
    catch (IOException e) {
      throw new ExecutionException(e);
    }
  }

  @Override
  public void logCommand() {
    super.logCommand();

    LOG.info("Terminal output " + ((TerminalProcessHandler) myHandler).getTerminalOutput());
  }

  private static List<String> escapeArguments(List<String> collection) {
    // TODO: Add additional checks like in java.lang.ProcessImpl constructor
    return ContainerUtil.map(collection, new Function<String, String>() {
      @Override
      public String fun(String s) {
        return s.contains(" ") ? "\"" + s + "\"" : s;
      }
    });
  }
}
