/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.EventDispatcher;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.VcsLocaleHelper;
import com.intellij.vcsUtil.VcsFileUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.properties.PropertyValue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class CommandExecutor {
  static final Logger LOG = Logger.getInstance(CommandExecutor.class.getName());
  private final AtomicReference<Integer> myExitCodeReference;

  @Nullable private String myMessage;
  private boolean myIsDestroyed;
  private boolean myNeedsDestroy;
  private volatile String myDestroyReason;
  private volatile boolean myWasCancelled;
  @NotNull private final List<File> myTempFiles;
  @NotNull protected final GeneralCommandLine myCommandLine;
  protected Process myProcess;
  protected SvnProcessHandler myHandler;
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
    if (myResultBuilder != null) {
      myListeners.addListener(myResultBuilder);
      // cancel tracker should be executed after result builder
      myListeners.addListener(new CommandCancelTracker());
    }
    myLock = new Object();
    myTempFiles = ContainerUtil.newArrayList();
    myCommandLine = createCommandLine();
    myCommandLine.setExePath(exePath);
    myCommandLine.setWorkDirectory(command.getWorkingDirectory());
    if (command.getConfigDir() != null) {
      myCommandLine.addParameters("--config-dir", command.getConfigDir().getPath());
    }
    myCommandLine.addParameter(command.getName().getName());
    myCommandLine.addParameters(prepareParameters(command));
    myExitCodeReference = new AtomicReference<>();
  }

  @NotNull
  private List<String> prepareParameters(@NotNull Command command) {
    List<String> parameters = command.getParameters();

    detectAndRemoveMessage(parameters);

    return parameters;
  }

  private void detectAndRemoveMessage(@NotNull List<String> parameters) {
    int index = parameters.indexOf("-m");
    index = index < 0 ? parameters.indexOf("--message") : index;

    if (index >= 0 && index + 1 < parameters.size()) {
      myMessage = parameters.get(index + 1);
      parameters.remove(index + 1);
      parameters.remove(index);
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

  public String getDestroyReason() {
    return myDestroyReason;
  }

  public void start() throws SvnBindException {
    synchronized (myLock) {
      checkNotStarted();

      try {
        beforeCreateProcess();
        myProcess = createProcess();
        if (LOG.isDebugEnabled()) {
          LOG.debug(myCommandLine.toString());
        }
        myHandler = createProcessHandler();
        myProcessWriter = new OutputStreamWriter(myHandler.getProcessInput());
        startHandlingStreams();
      }
      catch (ExecutionException e) {
        // TODO: currently startFailed() is not used for some real logic in svn4idea plugin
        listeners().startFailed(e);
        throw new SvnBindException(e);
      }
    }
  }

  protected void cleanup() {
    deleteTempFiles();
  }

  protected void beforeCreateProcess() throws SvnBindException {
    setupLocale();
    ensureMessageFile();
    ensureTargetsAdded();
    ensurePropertyValueAdded();
  }

  private void setupLocale() {
    myCommandLine.withEnvironment(VcsLocaleHelper.getDefaultLocaleEnvironmentVars("svn"));
  }

  @NotNull
  private File ensureCommandFile(@NotNull String prefix,
                                 @NotNull String extension,
                                 @NotNull String data,
                                 @NotNull String parameterName) throws SvnBindException {
    File result = createTempFile(prefix, extension);
    myTempFiles.add(result);

    try {
      FileUtil.writeToFile(result, data);
    }
    catch (IOException e) {
      throw new SvnBindException(e);
    }

    myCommandLine.addParameters(parameterName, result.getAbsolutePath());

    return result;
  }

  private void ensureMessageFile() throws SvnBindException {
    if (myMessage != null) {
      ensureCommandFile("commit-message", ".txt", myMessage, "-F");

      myCommandLine.addParameters("--config-option", "config:miscellany:log-encoding=" + CharsetToolkit.UTF8);
    }
  }

  private void ensureTargetsAdded() throws SvnBindException {
    List<String> targetsPaths = myCommand.getTargetsPaths();

    if (!ContainerUtil.isEmpty(targetsPaths)) {
      String targetsValue = StringUtil.join(targetsPaths, SystemProperties.getLineSeparator());

      if (myCommandLine.getCommandLineString().length() + targetsValue.length() > VcsFileUtil.FILE_PATH_LIMIT) {
        ensureCommandFile("command-targets", ".txt", targetsValue, "--targets");
      }
      else {
        myCommandLine.addParameters(targetsPaths);
      }
    }
  }

  private void ensurePropertyValueAdded() throws SvnBindException {
    PropertyValue propertyValue = myCommand.getPropertyValue();

    if (propertyValue != null) {
      ensureCommandFile("property-value", ".txt", PropertyValue.toString(propertyValue), "-F");
    }
  }

  private void deleteTempFiles() {
    for (File file : myTempFiles) {
      deleteTempFile(file);
    }
  }

  @NotNull
  protected static File getSvnFolder() {
    File vcsFolder = new File(PathManager.getSystemPath(), "vcs");

    return new File(vcsFolder, "svn");
  }

  @NotNull
  protected static File createTempFile(@NotNull String prefix, @NotNull String extension) throws SvnBindException {
    try {
      return FileUtil.createTempFile(getSvnFolder(), prefix, extension);
    }
    catch (IOException e) {
      throw new SvnBindException(e);
    }
  }

  protected static void deleteTempFile(@Nullable File file) {
    if (file != null) {
      boolean wasDeleted = FileUtil.delete(file);

      if (!wasDeleted) {
        LOG.info("Failed to delete temp file " + file.getAbsolutePath());
      }
    }
  }

  @NotNull
  protected SvnProcessHandler createProcessHandler() {
    return new SvnProcessHandler(myProcess, myCommandLine.getCommandLineString(), needsUtf8Output(), needsBinaryOutput());
  }

  protected boolean needsBinaryOutput() {
    // TODO: Add ability so that command could indicate output type it needs by itself
    return myCommand.is(SvnCommandName.cat) || (myCommand.is(SvnCommandName.diff) && !myCommand.getParameters().contains("--xml"));
  }

  protected boolean needsUtf8Output() {
    return myCommand.getParameters().contains("--xml");
  }

  @NotNull
  protected GeneralCommandLine createCommandLine() {
    return new GeneralCommandLine();
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

  public ProcessOutput getProcessOutput() {
    return outputAdapter.getOutput();
  }

  @NotNull
  public ByteArrayOutputStream getBinaryOutput() {
    return myHandler.getBinaryOutput();
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
    try {
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
    finally {
      cleanup();
    }
  }

  public void run(int timeout) throws SvnBindException {
    try {
      start();
      boolean finished = waitFor(timeout);
      if (!finished) {
        outputAdapter.getOutput().setTimeout();
        doDestroyProcess();
      }
    }
    finally {
      cleanup();
    }
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
      catch (ProcessCanceledException e) {
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
    if (!isStarted()) {
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
    public void processTerminated(@NotNull ProcessEvent event) {
      setExitCodeReference(event.getExitCode());
    }

    @Override
    public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
      if (ProcessOutputTypes.STDERR == outputType) {
        myWasError.set(true);
      }
    }
  }
}
