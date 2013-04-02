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

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessListener;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vcs.ProcessEventListener;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 1/25/12
 * Time: 12:58 PM
 */
public abstract class SvnCommand {
  static final Logger LOG = Logger.getInstance(SvnCommand.class.getName());

  private boolean myIsDestroyed;
  private int myExitCode;
  protected final GeneralCommandLine myCommandLine;
  private final File myWorkingDirectory;
  private Process myProcess;
  private OSProcessHandler myHandler;
  private final Object myLock;

  private final EventDispatcher<ProcessEventListener> myListeners = EventDispatcher.create(ProcessEventListener.class);

  // todo check version
  /*c:\Program Files (x86)\CollabNet\Subversion Client17>svn --version --quiet
  1.7.2*/

  public SvnCommand(File workingDirectory, @NotNull SvnCommandName commandName, @NotNull @NonNls String exePath) {
    this(workingDirectory, commandName, exePath, null);
  }

  public SvnCommand(File workingDirectory, @NotNull SvnCommandName commandName, @NotNull @NonNls String exePath,
                    @Nullable File configDir) {
    myLock = new Object();
    myCommandLine = new GeneralCommandLine();
    myWorkingDirectory = workingDirectory;
    myCommandLine.setExePath(exePath);
    myCommandLine.setWorkDirectory(workingDirectory);
    if (configDir != null) {
      myCommandLine.addParameters("--config-dir", configDir.getPath());
    }
    myCommandLine.addParameter(commandName.getName());
  }

  public void start() {
    synchronized (myLock) {
      checkNotStarted();

      try {
        myProcess = myCommandLine.createProcess();
        if (LOG.isDebugEnabled()) {
          LOG.debug(myCommandLine.toString());
        }
        myHandler = new OSProcessHandler(myProcess, myCommandLine.getCommandLineString());
        startHandlingStreams();
      } catch (Throwable t) {
        myListeners.getMulticaster().startFailed(t);
      }
    }
  }

  private void startHandlingStreams() {
    final ProcessListener processListener = new ProcessListener() {
      public void startNotified(final ProcessEvent event) {
        // do nothing
      }

      public void processTerminated(final ProcessEvent event) {
        final int exitCode = event.getExitCode();
        try {
          setExitCode(exitCode);
          SvnCommand.this.processTerminated(exitCode);
        } finally {
          listeners().processTerminated(exitCode);
        }
      }

      public void processWillTerminate(final ProcessEvent event, final boolean willBeDestroyed) {
        // do nothing
      }

      public void onTextAvailable(final ProcessEvent event, final Key outputType) {
        SvnCommand.this.onTextAvailable(event.getText(), outputType);
      }
    };

    myHandler.addProcessListener(processListener);
    myHandler.startNotify();
  }

  /**
   * Wait for process termination
   * @param timeout
   */
  public boolean waitFor(int timeout) {
    checkStarted();
    final OSProcessHandler handler;
    synchronized (myLock) {
      if (myIsDestroyed) return true;
      handler = myHandler;
    }
    if (timeout == -1) {
      return handler.waitFor();
    }
    else {
      return handler.waitFor(timeout);
    }
  }

  protected abstract void processTerminated(int exitCode);
  protected abstract void onTextAvailable(final String text, final Key outputType);

  public void cancel() {
    synchronized (myLock) {
      checkStarted();
      destroyProcess();
    }
  }
  
  protected void setExitCode(final int code) {
    synchronized (myLock) {
      myExitCode = code;
    }
  }

  public void addListener(final ProcessEventListener listener) {
    synchronized (myLock) {
      myListeners.addListener(listener);
    }
  }

  protected ProcessEventListener listeners() {
    synchronized (myLock) {
      return myListeners.getMulticaster();
    }
  }

  public void addParameters(@NonNls @NotNull String... parameters) {
    synchronized (myLock) {
      checkNotStarted();
      myCommandLine.addParameters(parameters);
    }
  }

  public void addParameters(List<String> parameters) {
    synchronized (myLock) {
      checkNotStarted();
      myCommandLine.addParameters(parameters);
    }
  }

  public void destroyProcess() {
    synchronized (myLock) {
      if (! myIsDestroyed) {
        myIsDestroyed = true;
        myHandler.destroyProcess();
      }
    }
  }

  /**
   * check that process is not started yet
   *
   * @throws IllegalStateException if process has been already started
   */
  private void checkNotStarted() {
    if (isStarted()) {
      throw new IllegalStateException("The process has been already started");
    }
  }

  /**
   * check that process is started
   *
   * @throws IllegalStateException if process has not been started
   */
  protected void checkStarted() {
    if (! isStarted()) {
      throw new IllegalStateException("The process is not started yet");
    }
  }

  /**
   * @return true if process is started
   */
  public boolean isStarted() {
    synchronized (myLock) {
      return myProcess != null;
    }
  }

  protected int getExitCode() {
    synchronized (myLock) {
      return myExitCode;
    }
  }

  protected File getWorkingDirectory() {
    return myWorkingDirectory;
  }
}
