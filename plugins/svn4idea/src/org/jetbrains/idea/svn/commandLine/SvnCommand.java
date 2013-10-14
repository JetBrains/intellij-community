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
import com.intellij.execution.process.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.LineHandlerHelper;
import com.intellij.openapi.vcs.ProcessEventListener;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 1/25/12
 * Time: 12:58 PM
 */
public class SvnCommand {
  static final Logger LOG = Logger.getInstance(SvnCommand.class.getName());
  private final AtomicReference<Integer> myExitCodeReference;
  private final File myConfigDir;

  private boolean myIsDestroyed;
  private boolean myNeedsDestroy;
  private int myExitCode;
  protected final GeneralCommandLine myCommandLine;
  private final File myWorkingDirectory;
  private Process myProcess;
  private OSProcessHandler myHandler;
  // TODO: Try to implement commands in a way that they manually indicate if they need full output - to prevent situations
  // TODO: when large amount of data needs to be stored instead of just sequential processing.
  private CapturingProcessAdapter outputAdapter;
  private final Object myLock;

  private final EventDispatcher<LineCommandListener> myListeners = EventDispatcher.create(LineCommandListener.class);
  private final SvnCommandName myCommandName;
  private String[] myOriginalParameters;

  /**
   * the partial line from stdout stream
   */
  private final StringBuilder myStdoutLine = new StringBuilder();
  /**
   * the partial line from stderr stream
   */
  private final StringBuilder myStderrLine = new StringBuilder();

  private final AtomicBoolean myWasError = new AtomicBoolean(false);

  public SvnCommand(File workingDirectory, @NotNull SvnCommandName commandName, @NotNull @NonNls String exePath) {
    this(workingDirectory, commandName, exePath, null);
  }

  public SvnCommand(File workingDirectory, @NotNull SvnCommandName commandName, @NotNull @NonNls String exePath,
                    @Nullable File configDir) {
    myCommandName = commandName;
    myLock = new Object();
    myCommandLine = new GeneralCommandLine();
    myWorkingDirectory = workingDirectory;
    myCommandLine.setExePath(exePath);
    myCommandLine.setWorkDirectory(workingDirectory);
    myConfigDir = configDir;
    if (configDir != null) {
      myCommandLine.addParameters("--config-dir", configDir.getPath());
    }
    myCommandLine.addParameter(commandName.getName());
    myExitCodeReference = new AtomicReference<Integer>();
  }

  public String[] getParameters() {
    synchronized (myLock) {
      return myCommandLine.getParametersList().getArray();
    }
  }

  /**
   * Indicates if process was destroyed "manually" by command execution logic.
   *
   * @return
   */
  public boolean isManuallyDestroyed() {
    return myIsDestroyed;
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
    outputAdapter = new CapturingProcessAdapter();
    myHandler.addProcessListener(outputAdapter);
    myHandler.addProcessListener(new ProcessEventTracker());
    myHandler.startNotify();
  }

  public String getOutput() {
    return outputAdapter.getOutput().getStdout();
  }

  public String getErrorOutput() {
    return outputAdapter.getOutput().getStderr();
  }

  /**
   * Wait for process termination
   * @param timeout
   */
  public boolean waitFor(int timeout) {
    checkStarted();
    final OSProcessHandler handler;
    synchronized (myLock) {
      // TODO: This line seems to cause situation when exitCode is not set before SvnLineCommand.runCommand() is finished.
      // TODO: Carefully analyze behavior (on all operating systems) and fix.
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

  protected void processTerminated(int exitCode) {
    // force newline
    if (myStdoutLine.length() != 0) {
      onTextAvailable("\n\r", ProcessOutputTypes.STDOUT);
    }
    else if (myStderrLine.length() != 0) {
      onTextAvailable("\n\r", ProcessOutputTypes.STDERR);
    }
  }

  protected void onTextAvailable(final String text, final Key outputType) {
    Iterator<String> lines = LineHandlerHelper.splitText(text).iterator();
    if (ProcessOutputTypes.STDOUT == outputType) {
      notifyLines(outputType, lines, myStdoutLine);
    }
    else if (ProcessOutputTypes.STDERR == outputType) {
      myWasError.set(true);
      notifyLines(outputType, lines, myStderrLine);
    }
  }

  private void notifyLines(final Key outputType, final Iterator<String> lines, final StringBuilder lineBuilder) {
    if (!lines.hasNext()) return;
    if (lineBuilder.length() > 0) {
      lineBuilder.append(lines.next());
      if (lines.hasNext()) {
        // line is complete
        final String line = lineBuilder.toString();
        notifyLine(line, outputType);
        lineBuilder.setLength(0);
      }
    }
    while (true) {
      String line = null;
      if (lines.hasNext()) {
        line = lines.next();
      }

      if (lines.hasNext()) {
        notifyLine(line, outputType);
      }
      else {
        if (line != null && line.length() > 0) {
          lineBuilder.append(line);
        }
        break;
      }
    }
  }

  private void notifyLine(final String line, final Key outputType) {
    String trimmed = LineHandlerHelper.trimLineSeparator(line);
    myListeners.getMulticaster().onLineAvailable(trimmed, outputType);
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

  public void addListener(final LineCommandListener listener) {
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
      myNeedsDestroy = true;
    }
  }

  /**
   * ProcessHandler.destroyProcess() implementations could acquire read lock in its implementation - like OSProcessManager.getInstance().
   * Some commands are called under write lock - which is generally bad idea, but such logic is not refactored yet.
   * To prevent deadlocks this method should only be called from thread that started the process.
   */
  public void doDestroyProcess() {
    synchronized (myLock) {
      if (!myIsDestroyed) {
        LOG.info("Destroying process by command: " + getCommandText());
        myIsDestroyed = true;
        myHandler.destroyProcess();
      }
    }
  }

  public boolean needsDestroy() {
    synchronized (myLock) {
      return myNeedsDestroy;
    }
  }

  // TODO: used only to ensure authentication info is not logged to file. Remove when command execution model is refactored
  // TODO: - so we could determine if parameter should be logged by the parameter itself.
  public void setOriginalParameters(String... original) {
    synchronized (myLock) {
      myOriginalParameters = original;
    }
  }

  public String getCommandText() {
    synchronized (myLock) {
      List<String> data = new ArrayList<String>();

      data.add(myCommandLine.getExePath());
      if (myConfigDir != null) {
        data.add("--config-dir");
        data.add(myConfigDir.getPath());
      }
      data.add(myCommandName.getName());
      if (myOriginalParameters != null) {
        data.addAll(Arrays.asList(myOriginalParameters));
      }

      return StringUtil.join(data, " ");
    }
  }

  public String getExePath() {
    synchronized (myLock) {
      return myCommandLine.getExePath();
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

  public SvnCommandName getCommandName() {
    return myCommandName;
  }

  public Integer getExitCodeReference() {
    return myExitCodeReference.get();
  }

  public void setExitCodeReference(int value) {
    myExitCodeReference.set(value);
  }

  public Boolean wasError() {
    return myWasError.get();
  }

  private class ProcessEventTracker implements ProcessListener {
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
  }
}
