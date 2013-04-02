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

import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vcs.ProcessEventListener;
import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 1/25/12
 * Time: 4:04 PM
 */
public class SvnSimpleCommand extends SvnCommand {
  private final StringBuilder myStderr;
  private final StringBuilder myStdout;
  private VcsException myException;
  private final Object myDataLock;

  public SvnSimpleCommand(File workingDirectory, @NotNull SvnCommandName commandName, @NotNull @NonNls String exePath) {
    super(workingDirectory, commandName, exePath);

    myDataLock = new Object();
    myStderr = new StringBuilder();
    myStdout = new StringBuilder();
  }

  @Override
  protected void processTerminated(int exitCode) {
    //
  }

  @Override
  protected void onTextAvailable(String text, Key outputType) {
    synchronized (myDataLock) {
      if (ProcessOutputTypes.STDOUT.equals(outputType)) {
        myStdout.append(text);
      } else if (ProcessOutputTypes.STDERR.equals(outputType)) {
        myStderr.append(text);
      }
    }
  }

  public StringBuilder getStderr() {
    synchronized (myDataLock) {
      return myStderr;
    }
  }

  public StringBuilder getStdout() {
    synchronized (myDataLock) {
      return myStdout;
    }
  }

  public String run() throws VcsException {
    addListener(new ProcessEventListener() {
      @Override
      public void processTerminated(int exitCode) {
      }

      @Override
      public void startFailed(Throwable exception) {
        synchronized (myDataLock) {
          myException = new VcsException("Process failed to start (" + myCommandLine.getCommandLineString() + "): " + exception.toString(), exception);
        }
      }
    });
    start();
    if (isStarted()) {//if wasn't started, exception is stored into a field, don't wait for process
      waitFor(-1);
    }

    synchronized (myDataLock) {
      if (myException != null) throw myException;
      final int code = getExitCode();
      if (code == 0) {
        return myStdout.toString();
      } else {
        final String msg = new StringBuilder("Svn process exited with error code: ").append(code).append("\n")
          .append("stderr: ").append(myStderr.toString()).append("\nstdout: ").append(getStdout().toString())
          .append("\nCommand was: ").append(myCommandLine.getCommandLineString()).toString();
        throw new VcsException(msg);
      }
    }
  }
}
