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

import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.ProcessWrapper;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.List;

/**
 * @author Konstantin Kolosovsky.
 */
public class WinTerminalExecutor extends TerminalExecutor {

  // max available value is 480
  // if greater value is provided than the default value of 80 will be assumed
  // this could provide unnecessary line breaks and thus could break parsing logic
  private static final int TERMINAL_WINDOW_MAX_COLUMNS = 480;

  static {
    // still use isWindows check here not to initialize corresponding property on non-Windows environments
    if (SystemInfo.isWindows) {
      System.setProperty("win.pty.cols", String.valueOf(TERMINAL_WINDOW_MAX_COLUMNS));
    }
  }

  @Nullable private File myRedirectFile;
  @Nullable private FileInputStream myRedirectStream;

  public WinTerminalExecutor(@NotNull @NonNls String exePath, @NotNull Command command) {
    super(exePath, command);
  }

  @NotNull
  @Override
  protected SvnProcessHandler createProcessHandler() {
    return new WinTerminalProcessHandler(myProcess, myCommandLine.getCommandLineString(), needsUtf8Output(), needsBinaryOutput());
  }

  @Override
  protected void beforeCreateProcess() throws SvnBindException {
    super.beforeCreateProcess();

    createRedirectFile();
  }

  private void createRedirectFile() throws SvnBindException {
    myRedirectFile = createTempFile("terminal-output", "");

    try {
      myRedirectStream = new FileInputStream(myRedirectFile);
    }
    catch (FileNotFoundException e) {
      throw new SvnBindException(e);
    }
  }

  @Override
  protected void cleanup() {
    super.cleanup();

    deleteRedirectFile();
  }

  private void deleteRedirectFile() {
    if (myRedirectStream != null) {
      try {
        myRedirectStream.close();
      }
      catch (IOException e) {
        LOG.info(e);
      }
    }

    deleteTempFile(myRedirectFile);
  }

  @NotNull
  @Override
  protected Process createProcess() throws ExecutionException {
    checkRedirectFile();

    List<String> parameters = escapeArguments(buildParameters());
    parameters.add(0, ExecUtil.getWindowsShellName());
    parameters.add(1, "/c");
    parameters.add(">>");
    //noinspection ConstantConditions
    parameters.add(quote(myRedirectFile.getAbsolutePath()));

    Process process = createProcess(parameters);

    return new ProcessWrapper(process) {
      @Override
      public InputStream getInputStream() {
        return myRedirectStream;
      }

      @Override
      public InputStream getErrorStream() {
        return getOriginalProcess().getInputStream();
      }
    };
  }

  private void checkRedirectFile() {
    if (myRedirectFile == null) {
      throw new IllegalStateException("No redirect file found");
    }
    if (myRedirectStream == null) {
      throw new IllegalStateException("No redirect stream found");
    }
  }

  /**
   * TODO: Identify pty4j quoting requirements for Windows and implement accordingly
   */
  @NotNull
  @Override
  protected List<String> escapeArguments(@NotNull List<String> arguments) {
    return ContainerUtil.map(arguments, new Function<String, String>() {
      @Override
      public String fun(String argument) {
        return needQuote(argument) && !isQuoted(argument) ? quote(argument) : argument;
      }
    });
  }

  @NotNull
  private static String quote(@NotNull String argument) {
    return StringUtil.wrapWithDoubleQuote(argument);
  }

  private static boolean needQuote(@NotNull String argument) {
    return argument.contains(" ");
  }

  private static boolean isQuoted(@NotNull String argument) {
    return StringUtil.startsWithChar(argument, '\"') && StringUtil.endsWithChar(argument, '\"');
  }
}
