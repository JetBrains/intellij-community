/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProcessEventListener;
import com.intellij.util.EventDispatcher;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnApplicationSettings;
import org.jetbrains.idea.svn.SvnVcs;

import java.io.File;
import java.io.OutputStream;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 1/25/12
 * Time: 12:58 PM
 */
public abstract class SvnCommand {
  private static final Logger LOG = Logger.getInstance(SvnCommand.class.getName());

  protected final Project myProject;
  protected final GeneralCommandLine myCommandLine;
  private final File myWorkingDirectory;
  protected Process myProcess;
  private final Object myLock;
  private Integer myExitCode; // exit code or null if exit code is not yet available

  private final EventDispatcher<ProcessEventListener> myListeners = EventDispatcher.create(ProcessEventListener.class);

  private Processor<OutputStream> myInputProcessor; // The processor for stdin

  // todo check version
  /*c:\Program Files (x86)\CollabNet\Subversion Client17>svn --version --quiet
  1.7.2*/

  public SvnCommand(Project project, File workingDirectory, @NotNull SvnCommandName commandName) {
    myLock = new Object();
    myProject = project;
    myCommandLine = new GeneralCommandLine();
    myWorkingDirectory = workingDirectory;
    final SvnApplicationSettings applicationSettings17 = SvnApplicationSettings.getInstance();
    myCommandLine.setExePath(applicationSettings17.getCommandLinePath());
    myCommandLine.setWorkDirectory(workingDirectory);
    myCommandLine.addParameter(commandName.getName());
  }

  public void start() {
    synchronized (myLock) {
      checkNotStarted();

      try {
        myProcess = startProcess();
        if (myProcess != null) {
          startHandlingStreams();
        } else {
          SvnVcs.getInstance(myProject).checkCommandLineVersion();
          myListeners.getMulticaster().startFailed(null);
        }
      } catch (Throwable t) {
        SvnVcs.getInstance(myProject).checkCommandLineVersion();
        myListeners.getMulticaster().startFailed(t);
      }
    }
  }

  /**
   * Wait for process termination
   */
  public void waitFor() {
    checkStarted();
    try {
      if (myInputProcessor != null && myProcess != null) {
        myInputProcessor.process(myProcess.getOutputStream());
      }
    }
    finally {
      waitForProcess();
    }
  }

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

  public abstract void destroyProcess();
  protected abstract void waitForProcess();

  protected abstract Process startProcess() throws ExecutionException;

  /**
   * Start handling process output streams for the handler.
   */
  protected abstract void startHandlingStreams();

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
}
