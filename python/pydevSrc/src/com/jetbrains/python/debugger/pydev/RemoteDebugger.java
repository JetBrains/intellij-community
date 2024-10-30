// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * Author: atotic
 * Created on Mar 23, 2004
 * License: Common Public License v1.0
 */
package com.jetbrains.python.debugger.pydev;

import com.google.common.collect.Maps;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.SuspendPolicy;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.jetbrains.python.console.pydev.PydevCompletionVariant;
import com.jetbrains.python.debugger.*;
import com.jetbrains.python.debugger.pydev.dataviewer.DataViewerCommand;
import com.jetbrains.python.debugger.pydev.dataviewer.DataViewerCommandBuilder;
import com.jetbrains.python.debugger.pydev.dataviewer.DataViewerCommandResult;
import com.jetbrains.python.debugger.pydev.transport.ClientModeDebuggerTransport;
import com.jetbrains.python.debugger.pydev.transport.DebuggerTransport;
import com.jetbrains.python.debugger.pydev.transport.ServerModeDebuggerTransport;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.jetbrains.python.tables.TableCommandParameters;
import com.jetbrains.python.tables.TableCommandType;

import java.net.ServerSocket;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.jetbrains.python.debugger.pydev.transport.BaseDebuggerTransport.logFrame;


public class RemoteDebugger implements ProcessDebugger {
  static final int RESPONSE_TIMEOUT = 60000;
  static final int SHORT_TIMEOUT = 2000;

  /**
   * The specific timeout for {@link VersionCommand} when IDE Python debugger
   * runs in <em>client mode</em>, which is used when debugging Python
   * applications run using Docker and Docker Compose.
   *
   * @see #handshake()
   * @see ClientModeDebuggerTransport
   */
  private static final long CLIENT_MODE_HANDSHAKE_TIMEOUT_IN_MILLIS = 5000;

  private static final Logger LOG = Logger.getInstance(RemoteDebugger.class);

  private static final String LOCAL_VERSION = "0.1";
  public static final String TEMP_VAR_PREFIX = "__py_debug_temp_var_";

  private static final SecureRandom ourRandom = new SecureRandom();

  private final IPyDebugProcess myDebugProcess;

  private int mySequence = -1;
  private final Object mySequenceObject = new Object(); // for synchronization on mySequence
  private final Map<String, PyThreadInfo> myThreads = new ConcurrentHashMap<>();
  private final Map<Integer, ProtocolFrame> myResponseQueue = new HashMap<>();
  private final TempVarsHolder myTempVars = new TempVarsHolder();

  private final Map<Pair<String, Integer>, String> myTempBreakpoints = Maps.newHashMap();


  private final List<RemoteDebuggerCloseListener> myCloseListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  @NotNull private final DebuggerTransport myDebuggerTransport;

  /**
   * The timeout for {@link VersionCommand}, which is used for handshaking with
   * the Python debugger script.
   *
   * @see #handshake()
   * @see #CLIENT_MODE_HANDSHAKE_TIMEOUT_IN_MILLIS
   * @see #RESPONSE_TIMEOUT
   */
  private final long myHandshakeTimeout;

  public RemoteDebugger(@NotNull IPyDebugProcess debugProcess, @NotNull String host, int port) {
    int connectRetryTimeout = Registry.intValue("python.debugger.remote.connect.retry.timeout.ms", 500);
    int connectMaxAttempts = Registry.intValue("python.debugger.remote.connect.max.attempts", 20);

    myDebugProcess = debugProcess;
    myDebuggerTransport = new ClientModeDebuggerTransport(this, host, port, Duration.ofMillis(connectRetryTimeout), connectMaxAttempts);
    myHandshakeTimeout = CLIENT_MODE_HANDSHAKE_TIMEOUT_IN_MILLIS;
  }

  public RemoteDebugger(@NotNull IPyDebugProcess debugProcess, @NotNull ServerSocket socket, int timeout) {
    myDebugProcess = debugProcess;
    myDebuggerTransport = new ServerModeDebuggerTransport(this, socket, timeout);
    myHandshakeTimeout = RESPONSE_TIMEOUT;
  }

  protected RemoteDebugger(@NotNull IPyDebugProcess debugProcess, @NotNull DebuggerTransport debuggerTransport) {
    myDebugProcess = debugProcess;
    myDebuggerTransport = debuggerTransport;
    myHandshakeTimeout = RESPONSE_TIMEOUT;
  }

  public IPyDebugProcess getDebugProcess() {
    return myDebugProcess;
  }

  @Override
  public boolean isConnected() {
    return myDebuggerTransport.isConnected();
  }

  @Override
  public void waitForConnect() throws Exception {
    myDebuggerTransport.waitForConnect();
  }

  private void writeToConsole(PyIo io) {
    ConsoleViewContentType contentType;
    if (io.getCtx() == 2) {
      contentType = ConsoleViewContentType.ERROR_OUTPUT;
    }
    else {
      contentType = ConsoleViewContentType.NORMAL_OUTPUT;
    }
    myDebugProcess.printToConsole(io.getText(), contentType);
  }

  @Override
  public String handshake() throws PyDebuggerException {
    final VersionCommand command = new VersionCommand(this, LOCAL_VERSION, SystemInfo.isUnix ? "UNIX" : "WIN", myHandshakeTimeout);
    command.execute();
    String version = command.getRemoteVersion();
    if (version != null) {
      version = version.trim();
    }
    return version;
  }

  @Override
  public PyDebugValue evaluate(final String threadId,
                               final String frameId,
                               final String expression, final boolean execute) throws PyDebuggerException {
    return evaluate(threadId, frameId, expression, execute, true);
  }


  @Override
  public PyDebugValue evaluate(final String threadId,
                               final String frameId,
                               final String expression,
                               final boolean execute,
                               boolean trimResult)
    throws PyDebuggerException {
    return executeCommand(new EvaluateCommand(this, threadId, frameId, expression, execute, trimResult)).getValue();
  }

  @Override
  public void consoleExec(String threadId, String frameId, String expression, PyDebugCallback<String> callback) {
    final ConsoleExecCommand command = new ConsoleExecCommand(this, threadId, frameId, expression);
    command.execute(callback);
  }

  @Override
  @Nullable
  public String execTableCommand(String threadId, String frameId, String command, TableCommandType commandType,
                                 TableCommandParameters tableCommandParameters) throws PyDebuggerException {
    final TableCommand tableCommand = new TableCommand(this, threadId, frameId, command, commandType, tableCommandParameters);
    tableCommand.execute();
    return tableCommand.getCommandResult();
  }

  @Override
  public XValueChildrenList loadFrame(final String threadId, final String frameId, GROUP_TYPE groupType) throws PyDebuggerException {
    return executeCommand(new GetFrameCommand(this, threadId, frameId, groupType)).getVariables();
  }

  @Override
  public List<Pair<String, Boolean>> getSmartStepIntoVariants(String threadId, String frameId, int startContextLine, int endContextLine)
    throws PyDebuggerException {
    return executeCommand(new GetSmartStepIntoVariantsCommand(this, threadId, frameId, startContextLine, endContextLine))
      .getVariants();
  }

  // todo: don't generate temp variables for qualified expressions - just split 'em
  @Override
  public XValueChildrenList loadVariable(final String threadId, final String frameId, final PyDebugValue var) throws PyDebuggerException {
    setTempVariable(threadId, frameId, var);
    return executeCommand(new GetVariableCommand(this, threadId, frameId, var)).getVariables();
  }

  @Override
  public ArrayChunk loadArrayItems(String threadId,
                                   String frameId,
                                   PyDebugValue var,
                                   int rowOffset,
                                   int colOffset,
                                   int rows,
                                   int cols,
                                   String format) throws PyDebuggerException {
    return executeCommand(new GetArrayCommand(this, threadId, frameId, var, rowOffset, colOffset, rows, cols, format)).getArray();
  }

  @Override
  @NotNull
  public DataViewerCommandResult executeDataViewerCommand(@NotNull DataViewerCommandBuilder builder) throws PyDebuggerException {
    builder.setDebugger(this);
    DataViewerCommand command = builder.build();
    command.execute();
    return command.getResult();
  }

  @Override
  public void loadReferrers(final String threadId,
                            final String frameId,
                            final PyReferringObjectsValue var,
                            final PyDebugCallback<? super XValueChildrenList> callback) {
    GetReferrersCommand cmd = new GetReferrersCommand(this, threadId, frameId, var);

    cmd.execute(new PyDebugCallback<>() {
      @Override
      public void ok(List<PyDebugValue> value) {
        XValueChildrenList list = new XValueChildrenList();
        for (PyDebugValue v : value) {
          list.add(v);
        }
        callback.ok(list);
      }

      @Override
      public void error(PyDebuggerException exception) {
        callback.error(exception);
      }
    });
  }

  @Override
  public PyDebugValue changeVariable(final String threadId, final String frameId, final PyDebugValue var, final String value)
    throws PyDebuggerException {
    setTempVariable(threadId, frameId, var);
    return doChangeVariable(threadId, frameId, var.getEvaluationExpression(), value);
  }

  private PyDebugValue doChangeVariable(final String threadId, final String frameId, final String varName, final String value)
    throws PyDebuggerException {
    return executeCommand(new ChangeVariableCommand(this, threadId, frameId, varName, value)).getNewValue();
  }

  @Override
  public void loadFullVariableValues(@NotNull String threadId,
                                     @NotNull String frameId,
                                     @NotNull List<PyFrameAccessor.PyAsyncValue<String>> vars) throws PyDebuggerException {
    executeCommand(new LoadFullValueCommand(this, threadId, frameId, vars));
  }

  @Override
  @Nullable
  public String loadSource(String path) {
    try {
      return executeCommand(new LoadSourceCommand(this, path)).getContent();
    }
    catch (PyDebuggerException e) {
      return "#Couldn't load source of file " + path;
    }
  }

  private void cleanUp() {
    myThreads.clear();
    myResponseQueue.clear();
    synchronized (mySequenceObject) {
      mySequence = -1;
    }
    myTempVars.clear();
  }

  // todo: change variable in lists doesn't work - either fix in pydevd or format var name appropriately
  private void setTempVariable(final String threadId, final String frameId, final PyDebugValue var) {
    final PyDebugValue topVar = var.getTopParent();
    if (topVar == null) {
      LOG.error("Top parent is null");
      return;
    }
    String tempName = topVar.getTempName();
    if (tempName != null) {
      return;
    }
    if (!myDebugProcess.canSaveToTemp(topVar.getName())) {
      return;
    }
    if (myTempVars.contains(threadId, frameId, tempName)) {
      return;
    }

    topVar.setTempName(generateTempName());
    try {
      doChangeVariable(threadId, frameId, topVar.getTempName(), topVar.getName());
      myTempVars.put(threadId, frameId, topVar.getTempName());
    }
    catch (PyDebuggerException e) {
      LOG.error(e);
      topVar.setTempName(null);
    }
  }

  public String generateSaveTempName(final String threadId, final String frameId) {
    final String tempName = generateTempName();
    myTempVars.put(threadId, frameId, tempName);
    return tempName;
  }

  private void clearTempVariables(final String threadId) {
    final Map<String, Set<String>> threadVars = myTempVars.get(threadId);
    if (threadVars == null || threadVars.isEmpty()) return;

    for (Map.Entry<String, Set<String>> entry : threadVars.entrySet()) {
      final Set<String> frameVars = entry.getValue();
      if (frameVars == null || frameVars.isEmpty()) continue;

      final String expression = "del " + StringUtil.join(frameVars, ",");
      final String wrappedExpression = String.format("try:\n    %s\nexcept:\n    pass", expression);
      try {
        evaluate(threadId, entry.getKey(), wrappedExpression, true);
      }
      catch (PyDebuggerException e) {
        LOG.error(e);
      }
    }

    myTempVars.clear(threadId);
  }

  private static String generateTempName() {
    return TEMP_VAR_PREFIX + ourRandom.nextInt(Integer.MAX_VALUE);
  }

  @Override
  public Collection<PyThreadInfo> getThreads() {
    return Collections.unmodifiableCollection(new ArrayList<>(myThreads.values()));
  }

  int getNextSequence() {
    synchronized (mySequenceObject) {
      mySequence += 2;
      return mySequence;
    }
  }

  void placeResponse(final int sequence, final ProtocolFrame response) {
    synchronized (myResponseQueue) {
      if (response == null || myResponseQueue.containsKey(sequence)) {
        myResponseQueue.put(sequence, response);
      }
      if (response != null) {
        myResponseQueue.notifyAll();
      }
    }
  }

  @Nullable
  ProtocolFrame waitForResponse(final int sequence, long timeout) {
    ProtocolFrame response;
    long until = System.currentTimeMillis() + timeout;

    synchronized (myResponseQueue) {
      boolean interrupted = false;
      do {
        try {
          myResponseQueue.wait(1000);
        }
        catch (InterruptedException e) {
          // restore interrupted flag
          Thread.currentThread().interrupt();

          interrupted = true;
        }
        response = myResponseQueue.get(sequence);
      }
      while (response == null && shouldWaitForResponse() && !interrupted && System.currentTimeMillis() < until);
      myResponseQueue.remove(sequence);
    }

    return response;
  }

  private boolean shouldWaitForResponse() {
    return myDebuggerTransport.isConnecting() || myDebuggerTransport.isConnected();
  }

  @Override
  public void execute(@NotNull final AbstractCommand command) {
    CountDownLatch myLatch = new CountDownLatch(1);
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      if (command instanceof ResumeOrStepCommand) {
        final String threadId = ((ResumeOrStepCommand)command).getThreadId();
        clearTempVariables(threadId);
      }

      try {
        command.execute();
      }
      catch (Exception e) {
        LOG.error(e);
      }
      finally {
        myLatch.countDown();
      }
    });
    if (command.isResponseExpected()) {
      ApplicationManager.getApplication().assertIsNonDispatchThread();

      // Note: do not wait for result from UI thread
      try {
        myLatch.await(command.getResponseTimeout(), TimeUnit.MILLISECONDS);
      }
      catch (InterruptedException e) {
        // restore interrupted flag
        Thread.currentThread().interrupt();

        LOG.error(e);
      }
    }
  }

  boolean sendFrame(final ProtocolFrame frame) {
    return myDebuggerTransport.sendFrame(frame);
  }

  @Override
  public void suspendAllThreads() {
    for (PyThreadInfo thread : getThreads()) {
      suspendThread(thread.getId());
    }
  }


  @Override
  public void suspendThread(String threadId) {
    final SuspendCommand command = new SuspendCommand(this, threadId);
    execute(command);
  }

  @Override
  public void close() {
    myDebuggerTransport.close();
    fireCloseEvent();
  }

  @Override
  public void disconnect() {
    myDebuggerTransport.disconnect();

    cleanUp();
  }

  @Override
  public void run() throws PyDebuggerException {
    executeCommand(new RunCommand(this));
  }

  @Override
  public void smartStepInto(String threadId, String frameId, String functionName, int callOrder, int contextStartLine, int contextEndLine) {
    final SmartStepIntoCommand command = new SmartStepIntoCommand(this, threadId, frameId, functionName, callOrder,
                                                                  contextStartLine, contextEndLine);
    execute(command);
  }

  @Override
  public void resumeOrStep(String threadId, ResumeOrStepCommand.Mode mode) {
    final ResumeOrStepCommand command = new ResumeOrStepCommand(this, threadId, mode);
    execute(command);
  }

  @Override
  public void setNextStatement(@NotNull String threadId,
                               @NotNull XSourcePosition sourcePosition,
                               @Nullable String functionName,
                               @NotNull PyDebugCallback<Pair<Boolean, String>> callback) {
    executeCommandSafely(new SetNextStatementCommand(this, threadId, sourcePosition, functionName, callback));
  }

  @Override
  public void setTempBreakpoint(@NotNull String type, @NotNull String file, int line) {
    executeCommandSafely(new SetBreakpointCommand(this, type, file, line));
    myTempBreakpoints.put(Pair.create(file, line), type);
  }

  @Override
  public void removeTempBreakpoint(@NotNull String file, int line) {
    String type = myTempBreakpoints.remove(Pair.create(file, line));
    if (type != null) {
      final RemoveBreakpointCommand command = new RemoveBreakpointCommand(this, type, file, line);
      execute(command);  // remove temp. breakpoint
    }
    else {
      LOG.warn("Temp breakpoint not found for " + file + ":" + line);
    }
  }

  @Override
  public void setBreakpoint(@NotNull String typeId,
                            @NotNull String file,
                            int line,
                            @Nullable String condition,
                            @Nullable String logExpression,
                            @Nullable String funcName,
                            @NotNull SuspendPolicy policy) {
    final SetBreakpointCommand command =
      new SetBreakpointCommand(this, typeId, file, line,
                               condition,
                               logExpression,
                               funcName,
                               policy);
    execute(command);
  }


  @Override
  public void removeBreakpoint(@NotNull String typeId, @NotNull String file, int line) {
    final RemoveBreakpointCommand command =
      new RemoveBreakpointCommand(this, typeId, file, line);
    execute(command);
  }

  @Override
  public void setUserTypeRenderers(@NotNull List<@NotNull PyUserTypeRenderer> renderers) {
    final SetUserTypeRenderersCommand command = new SetUserTypeRenderersCommand(this, renderers);
    execute(command);
  }

  @Override
  public void setShowReturnValues(boolean isShowReturnValues) {
    final ShowReturnValuesCommand command = new ShowReturnValuesCommand(this, isShowReturnValues);
    execute(command);
  }

  @Override
  public void setUnitTestDebuggingMode() {
    SetUnitTestDebuggingMode command = new SetUnitTestDebuggingMode(this);
    execute(command);
  }

  // for DebuggerReader only
  public void processResponse(@NotNull final String line) {
    try {
      final ProtocolFrame frame = new ProtocolFrame(line);
      logFrame(frame, false);

      if (AbstractThreadCommand.isThreadCommand(frame.getCommand())) {
        processThreadEvent(frame);
      }
      else if (AbstractCommand.isWriteToConsole(frame.getCommand())) {
        writeToConsole(ProtocolParser.parseIo(frame.getPayload()));
      }
      else if (AbstractCommand.isExitEvent(frame.getCommand())) {
        fireCommunicationError();
      }
      else if (AbstractCommand.isCallSignatureTrace(frame.getCommand())) {
        recordCallSignature(ProtocolParser.parseCallSignature(frame.getPayload()));
      }
      else if (AbstractCommand.isConcurrencyEvent(frame.getCommand())) {
        recordConcurrencyEvent(ProtocolParser.parseConcurrencyEvent(frame.getPayload(), myDebugProcess.getPositionConverter()));
      }
      else if (AbstractCommand.isInputRequested(frame.getCommand())) {
        myDebugProcess.consoleInputRequested(ProtocolParser.parseInputCommand(frame.getPayload()));
      }
      else if (ProcessCreatedCommand.isProcessCreatedCommand(frame.getCommand())) {
        onProcessCreatedEvent(frame.getSequence());
      }
      else if (AbstractCommand.isShowWarningCommand(frame.getCommand())) {
        final String warningId = ProtocolParser.parseWarning(frame.getPayload());
        myDebugProcess.showWarning(warningId);
      }
      else {
        placeResponse(frame.getSequence(), frame);
      }
    }
    catch (Throwable t) {
      // shouldn't interrupt reader thread
      LOG.error(t);
    }
  }

  private void recordCallSignature(PySignature signature) {
    myDebugProcess.recordSignature(signature);
  }

  private void recordConcurrencyEvent(PyConcurrencyEvent event) {
    myDebugProcess.recordLogEvent(event);
  }

  // todo: extract response processing
  private void processThreadEvent(ProtocolFrame frame) throws PyDebuggerException {
    // The method must be synchronized because in the case of multiprocess debugging,
    // each process `RemoteDebugger` shares the same session. Altering the session's state
    // in an unsynchronized manner can cause race conditions.
    synchronized (myDebugProcess.getSession()) {
      switch (frame.getCommand()) {
        case AbstractCommand.CREATE_THREAD -> {
          final PyThreadInfo thread = parseThreadEvent(frame);
          if (!thread.isPydevThread()) {  // ignore pydevd threads
            myThreads.put(thread.getId(), thread);
            if (myDebugProcess.getSession().isSuspended() && myDebugProcess.isSuspendedOnAllThreadsPolicy()) {
              // Sometimes the notification about new threads may come slow from the Python side. We should check if
              // the current session is suspended in the "Suspend all threads" mode and suspend new thread, which hasn't been suspended
              suspendThread(thread.getId());
            }
          }
        }
        case AbstractCommand.SUSPEND_THREAD -> {
          final PyThreadInfo event = parseThreadEvent(frame);
          PyThreadInfo thread = myThreads.get(event.getId());
          if (thread == null) {
            LOG.error("Trying to stop on non-existent thread: " + event.getId() + ", " + event.getStopReason() + ", " + event.getMessage());
            myThreads.put(event.getId(), event);
            thread = event;
          }
          thread.updateState(PyThreadInfo.State.SUSPENDED, event.getFrames());
          thread.setStopReason(event.getStopReason());
          thread.setMessage(event.getMessage());
          boolean updateSourcePosition = true;
          if (event.getStopReason() == AbstractCommand.SUSPEND_THREAD || event.getStopReason() == AbstractCommand.SET_BREAKPOINT) {
            updateSourcePosition = !myDebugProcess.getSession().isSuspended();
          }
          myDebugProcess.threadSuspended(thread, updateSourcePosition);
        }
        case AbstractCommand.RESUME_THREAD -> {
          final String id = ProtocolParser.getThreadId(frame.getPayload());
          final PyThreadInfo thread = myThreads.get(id);
          if (thread != null) {
            thread.updateState(PyThreadInfo.State.RUNNING, null);
            myDebugProcess.threadResumed(thread);
          }
        }
        case AbstractCommand.KILL_THREAD -> {
          final String id = frame.getPayload();
          final PyThreadInfo thread = myThreads.get(id);
          if (thread != null) {
            thread.updateState(PyThreadInfo.State.KILLED, null);
            myThreads.remove(id);
          }
          if (myDebugProcess.getSession().getCurrentPosition() == null) {
            for (PyThreadInfo threadInfo : myThreads.values()) {
              // notify UI of suspended threads left in debugger if one thread finished its work
              if ((threadInfo != null) && (threadInfo.getState() == PyThreadInfo.State.SUSPENDED)) {
                myDebugProcess.threadResumed(threadInfo);
                myDebugProcess.threadSuspended(threadInfo, true);
              }
            }
          }
        }
        case AbstractCommand.SHOW_CONSOLE -> {
          final PyThreadInfo event = parseThreadEvent(frame);
          PyThreadInfo thread = myThreads.get(event.getId());
          if (thread == null) {
            myThreads.put(event.getId(), event);
            thread = event;
          }
          thread.updateState(PyThreadInfo.State.SUSPENDED, event.getFrames());
          thread.setStopReason(event.getStopReason());
          thread.setMessage(event.getMessage());
          myDebugProcess.showConsole(thread);
        }
      }
    }
  }

  private PyThreadInfo parseThreadEvent(ProtocolFrame frame) throws PyDebuggerException {
    return ProtocolParser.parseThread(frame.getPayload(), myDebugProcess.getPositionConverter());
  }

  private static class TempVarsHolder {
    private final Map<String, Map<String, Set<String>>> myData = new HashMap<>();

    public boolean contains(final String threadId, final String frameId, final String name) {
      final Map<String, Set<String>> threadVars = myData.get(threadId);
      if (threadVars == null) return false;

      final Set<String> frameVars = threadVars.get(frameId);
      if (frameVars == null) return false;

      return frameVars.contains(name);
    }

    protected void put(final String threadId, final String frameId, final String name) {
      Map<String, Set<String>> threadVars = myData.get(threadId);
      if (threadVars == null) myData.put(threadId, (threadVars = new HashMap<>()));

      Set<String> frameVars = threadVars.get(frameId);
      if (frameVars == null) threadVars.put(frameId, (frameVars = new HashSet<>()));

      frameVars.add(name);
    }

    protected Map<String, Set<String>> get(final String threadId) {
      return myData.get(threadId);
    }

    protected void clear() {
      myData.clear();
    }

    protected void clear(final String threadId) {
      final Map<String, Set<String>> threadVars = myData.get(threadId);
      if (threadVars != null) {
        threadVars.clear();
      }
    }
  }

  @Override
  public void addCloseListener(RemoteDebuggerCloseListener listener) {
    myCloseListeners.add(listener);
  }

  public void removeCloseListener(RemoteDebuggerCloseListener listener) {
    myCloseListeners.remove(listener);
  }

  @Override
  public List<PydevCompletionVariant> getCompletions(String threadId, String frameId, String prefix) {
    final GetCompletionsCommand command = new GetCompletionsCommand(this, threadId, frameId, prefix);
    execute(command);
    return command.getCompletions();
  }

  @Override
  public String getDescription(String threadId, String frameId, String cmd) {
    final GetDescriptionCommand command = new GetDescriptionCommand(this, threadId, frameId, cmd);
    execute(command);
    return command.getResult();
  }

  @Override
  public void addExceptionBreakpoint(ExceptionBreakpointCommandFactory factory) {
    execute(factory.createAddCommand(this));
  }

  @Override
  public void removeExceptionBreakpoint(ExceptionBreakpointCommandFactory factory) {
    execute(factory.createRemoveCommand(this));
  }

  @Override
  public void suspendOtherThreads(PyThreadInfo thread) {
    if (!myThreads.containsKey(thread.getId())) {
      // It means that breakpoint with "Suspend all" policy was reached in another process
      // and we should suspend all threads in the current process on Java side
      for (PyThreadInfo otherThread : getThreads()) {
        if (!otherThread.getId().equals(thread.getId())) {
          suspendThread(otherThread.getId());
        }
      }
    }
  }

  protected void onProcessCreatedEvent(int commandSequence) {
  }

  protected void fireCloseEvent() {
    for (RemoteDebuggerCloseListener listener : myCloseListeners) {
      listener.closed();
    }
  }

  public void fireCommunicationError() {
    for (RemoteDebuggerCloseListener listener : myCloseListeners) {
      listener.communicationError();
    }
  }

  // for DebuggerReader only
  public void fireExitEvent() {
    for (RemoteDebuggerCloseListener listener : myCloseListeners) {
      listener.detached();
    }
  }

  /**
   * Executes the command and returns it.
   * <p>
   * If the command execution throws an exception and the debugger is in
   * "connected" state then the exception is rethrown. If the debugger is not
   * connected at this moment then the exception is ignored.
   *
   */
  private <T extends AbstractCommand<?>> T executeCommand(@NotNull T command) throws PyDebuggerException {
    try {
      command.execute();
    }
    catch (PyDebuggerException e) {
      if (isConnected()) {
        throw e;
      }
    }
    return command;
  }

  /**
   * Executes the command safely. In case of {@link PyDebuggerException}, the
   * exception is only logged.
   * <p>
   * If the command execution throws an exception and the debugger is in
   * "connected" state then the error is logged. If the debugger is not
   * connected at this moment then the exception is ignored.
   *
   */
  private <T extends AbstractCommand<?>> void executeCommandSafely(@NotNull T command) {
    try {
      command.execute();
    }
    catch (PyDebuggerException e) {
      if (isConnected()) {
        LOG.error("Command " + command + " failed", e);
      }
    }
  }

  @Override
  public void interruptDebugConsole() {
    InterruptDebugConsoleCommand interruptCommand = new InterruptDebugConsoleCommand(this);
    try {
      interruptCommand.execute();
    }
    catch (PyDebuggerException e) {
      LOG.error(e);
    }
  }
}
