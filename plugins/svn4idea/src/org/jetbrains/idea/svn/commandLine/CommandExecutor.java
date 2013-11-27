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

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.EventDispatcher;
import com.intellij.util.io.BaseDataReader;
import com.intellij.util.io.BinaryOutputReader;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNCancelException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 1/25/12
 * Time: 12:58 PM
 */
public class CommandExecutor {
  static final Logger LOG = Logger.getInstance(CommandExecutor.class.getName());
  private final AtomicReference<Integer> myExitCodeReference;

  private boolean myIsDestroyed;
  private boolean myNeedsDestroy;
  private volatile String myDestroyReason;
  private volatile boolean myWasCancelled;
  protected final GeneralCommandLine myCommandLine;
  protected Process myProcess;
  protected OSProcessHandler myHandler;
  private OutputStreamWriter myProcessWriter;
  // TODO: Try to implement commands in a way that they manually indicate if they need full output - to prevent situations
  // TODO: when large amount of data needs to be stored instead of just sequential processing.
  private CapturingProcessAdapter outputAdapter;
  private final Object myLock;

  private final EventDispatcher<LineCommandListener> myListeners = EventDispatcher.create(LineCommandListener.class);

  private final AtomicBoolean myWasError = new AtomicBoolean(false);
  @Nullable private final LineCommandListener myResultBuilder;
  @NotNull private final Command myCommand;

  public CommandExecutor(@NotNull @NonNls String exePath, @NotNull Command command) {
    myCommand = command;
    myResultBuilder = command.getResultBuilder();
    if (myResultBuilder != null)
    {
      myListeners.addListener(myResultBuilder);
      // cancel tracker should be executed after result builder
      myListeners.addListener(new CommandCancelTracker());
    }
    myLock = new Object();
    myCommandLine = new GeneralCommandLine();
    myCommandLine.setExePath(exePath);
    myCommandLine.setWorkDirectory(command.getWorkingDirectory());
    if (command.getConfigDir() != null) {
      myCommandLine.addParameters("--config-dir", command.getConfigDir().getPath());
    }
    myCommandLine.addParameter(command.getName().getName());
    myCommandLine.addParameters(command.getParameters());
    myExitCodeReference = new AtomicReference<Integer>();
  }

  /**
   * Indicates if process was destroyed "manually" by command execution logic.
   *
   * @return
   */
  public boolean isManuallyDestroyed() {
    return myIsDestroyed;
  }

  public String getDestroyReason() {
    return myDestroyReason;
  }

  public void start() throws SvnBindException {
    synchronized (myLock) {
      checkNotStarted();

      try {
        myProcess = createProcess();
        if (LOG.isDebugEnabled()) {
          LOG.debug(myCommandLine.toString());
        }
        myHandler = createProcessHandler();
        myProcessWriter = new OutputStreamWriter(myHandler.getProcessInput());
        startHandlingStreams();
      } catch (ExecutionException e) {
        // TODO: currently startFailed() is not used for some real logic in svn4idea plugin
        listeners().startFailed(e);
        throw new SvnBindException(e);
      }
    }
  }

  @NotNull
  protected OSProcessHandler createProcessHandler() {
    return needsBinaryOutput()
           ? new BinaryOSProcessHandler(myProcess, myCommandLine.getCommandLineString())
           : new OSProcessHandler(myProcess, myCommandLine.getCommandLineString());
  }

  private boolean needsBinaryOutput() {
    return SvnCommandName.cat.equals(myCommand.getName());
  }

  @NotNull
  protected Process createProcess() throws ExecutionException {
    return myCommandLine.createProcess();
  }

  protected void startHandlingStreams() {
    outputAdapter = new CapturingProcessAdapter();
    myHandler.addProcessListener(outputAdapter);
    myHandler.addProcessListener(new ProcessTracker());
    myHandler.addProcessListener(new ResultBuilderNotifier(listeners()));
    myHandler.addProcessListener(new CommandOutputLogger());
    myHandler.startNotify();
  }

  public String getOutput() {
    return outputAdapter.getOutput().getStdout();
  }

  public String getErrorOutput() {
    return outputAdapter.getOutput().getStderr();
  }

  @Nullable
  public ByteArrayOutputStream getBinaryOutput() {
    return myHandler instanceof BinaryOSProcessHandler ? ((BinaryOSProcessHandler)myHandler).myBinaryOutput : null;
  }

  // TODO: Carefully here - do not modify command from threads other than the one started command execution
  @NotNull
  public Command getCommand() {
    return myCommand;
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

  public void cancel() {
    synchronized (myLock) {
      checkStarted();
      destroyProcess();
    }
  }

  public void run() throws SvnBindException {
    start();
    boolean finished;
    do {
      finished = waitFor(500);
      if (!finished && (wasError() || needsDestroy() || checkCancelled())) {
        waitFor(1000);
        doDestroyProcess();
        break;
      }
    }
    while (!finished);
  }

  public void addListener(final LineCommandListener listener) {
    synchronized (myLock) {
      myListeners.addListener(listener);
    }
  }

  protected LineCommandListener listeners() {
    synchronized (myLock) {
      return myListeners.getMulticaster();
    }
  }

  public boolean checkCancelled() {
    if (!myWasCancelled && myCommand.getCanceller() != null) {
      try {
        myCommand.getCanceller().checkCancelled();
      }
      catch (SVNCancelException e) {
        // indicates command should be cancelled
        myWasCancelled = true;
      }
    }

    return myWasCancelled;
  }

  public void destroyProcess() {
    synchronized (myLock) {
      myNeedsDestroy = true;
    }
  }

  public void destroyProcess(@Nullable String destroyReason) {
    synchronized (myLock) {
      myDestroyReason = destroyReason;
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

  public String getCommandText() {
    synchronized (myLock) {
      return StringUtil.join(myCommandLine.getExePath(), " ", myCommand.getText());
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

  public SvnCommandName getCommandName() {
    return myCommand.getName();
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

  public void write(String value) throws SvnBindException {
    try {
      synchronized (myLock) {
        myProcessWriter.write(value);
        myProcessWriter.flush();
      }
    }
    catch (IOException e) {
      throw new SvnBindException(e);
    }
  }

  public void logCommand() {
    LOG.info("Command text " + getCommandText());
    LOG.info("Command output " + getOutput());
  }

  private class CommandCancelTracker extends LineCommandAdapter {
    @Override
    public void onLineAvailable(String line, Key outputType) {
      if (myResultBuilder != null && myResultBuilder.isCanceled()) {
        LOG.info("Cancelling command: " + getCommandText());
        destroyProcess();
      }
    }
  }

  private class ProcessTracker extends ProcessAdapter {

    @Override
    public void processTerminated(ProcessEvent event) {
      setExitCodeReference(event.getExitCode());
    }

    @Override
    public void onTextAvailable(ProcessEvent event, Key outputType) {
      if (ProcessOutputTypes.STDERR == outputType) {
        myWasError.set(true);
      }
    }
  }

  private static class BinaryOSProcessHandler extends OSProcessHandler {

    @NotNull private final ByteArrayOutputStream myBinaryOutput;

    public BinaryOSProcessHandler(@NotNull final Process process, @Nullable final String commandLine) {
      super(process, commandLine);
      myBinaryOutput = new ByteArrayOutputStream();
    }

    @NotNull
    @Override
    protected BaseDataReader createOutputDataReader(BaseDataReader.SleepingPolicy sleepingPolicy) {
      return new SimpleBinaryOutputReader(myProcess.getInputStream(), sleepingPolicy);
    }

    private class SimpleBinaryOutputReader extends BinaryOutputReader {

      public SimpleBinaryOutputReader(@NotNull InputStream stream, SleepingPolicy sleepingPolicy) {
        super(stream, sleepingPolicy);
        start();
      }

      @Override
      protected void onBinaryAvailable(@NotNull byte[] data, int size) {
        myBinaryOutput.write(data, 0, size);
      }

      @Override
      protected Future<?> executeOnPooledThread(Runnable runnable) {
        return BinaryOSProcessHandler.this.executeOnPooledThread(runnable);
      }
    }
  }
}
