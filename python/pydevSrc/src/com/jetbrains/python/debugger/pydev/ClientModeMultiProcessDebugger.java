// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger.pydev;

import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.SuspendPolicy;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.jetbrains.python.console.pydev.PydevCompletionVariant;
import com.jetbrains.python.debugger.*;
import com.jetbrains.python.debugger.pydev.dataviewer.DataViewerCommandBuilder;
import com.jetbrains.python.debugger.pydev.dataviewer.DataViewerCommandResult;
import com.jetbrains.python.tables.TableCommandParameters;
import com.jetbrains.python.tables.TableCommandType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;

/**
 * @see com.jetbrains.python.debugger.pydev.transport.ClientModeDebuggerTransport
 */
public class ClientModeMultiProcessDebugger implements ProcessDebugger {
  private static final Logger LOG = Logger.getInstance(ClientModeMultiProcessDebugger.class);

  private final IPyDebugProcess myDebugProcess;
  private final @NotNull String myHost;
  private final int myPort;

  private final Object myDebuggersObject = new Object();
  /**
   * Guarded by {@link #myDebuggersObject}.
   */
  private final List<RemoteDebugger> myDebuggers = new ArrayList<>();

  private final ThreadRegistry myThreadRegistry = new ThreadRegistry();

  private final ClientModeDebuggerStatusHolder myDebuggerStatusHolder = new ClientModeDebuggerStatusHolder();

  private final CompositeRemoteDebuggerCloseListener myCompositeListener = new CompositeRemoteDebuggerCloseListener();

  private final @NotNull ExecutorService myExecutor;

  private class ConnectToDebuggerTask implements Runnable {
    @Override
    public void run() {
      try {
        tryToConnectRemoteDebugger();
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
  }

  public ClientModeMultiProcessDebugger(final @NotNull IPyDebugProcess debugProcess,
                                        @NotNull String host, int port) {
    myDebugProcess = debugProcess;
    myHost = host;
    myPort = port;

    String connectionThreadsName = "Debugger connection thread (" + host + ":" + port + ")";

    myExecutor = ConcurrencyUtil.newSingleThreadExecutor(connectionThreadsName);

    myExecutor.execute(new ConnectToDebuggerTask());
  }

  /**
   * Should either successfully connect to the debugger script or throw {@link IOException} because of the
   * connection error or the socket timeout error.
   * <p>
   * We assume that debugger is successfully connected if the Python debugger
   * script responded on `CMD_VERSION` command (see {@link RemoteDebugger#handshake()}).
   *
   * @throws Exception if the connection or timeout error occurred
   * @see com.jetbrains.python.debugger.pydev.transport.ClientModeDebuggerTransport
   */
  private void tryToConnectRemoteDebugger() throws Exception {
    RemoteDebugger debugger = new RemoteDebugger(myDebugProcess, myHost, myPort) {
      @Override
      protected void onProcessCreatedEvent(int commandSequence) {
        try {
          ProcessCreatedMsgReceivedCommand command = new ProcessCreatedMsgReceivedCommand(this, commandSequence);
          command.execute();
          myExecutor.execute(new ConnectToDebuggerTask());
        }
        catch (PyDebuggerException e) {
          LOG.info(e);
        }
      }
    };
    debugger.waitForConnect();
    onRemoteDebuggerConnected(debugger);
  }

  private void onRemoteDebuggerConnected(@NotNull RemoteDebugger debugger) {
    // register successfully connected debugger
    synchronized (myDebuggersObject) {
      myDebuggers.add(debugger);
    }

    // notify `waitForConnect()` that we connected
    if (myDebuggerStatusHolder.onConnected()) {
      // add close listeners for the first accepted debugger
      debugger.addCloseListener(myCompositeListener);
    }
    else {
      // for consequent processes we should init them by ourselves
      myDebugProcess.init();
      try {
        debugger.run();
      }
      catch (PyDebuggerException e) {
        LOG.debug("Failed to `CMD_RUN` the recently connected Python debugger process");
      }
    }
  }

  @Override
  public boolean isConnected() {
    return ContainerUtil.exists(allDebuggers(), RemoteDebugger::isConnected);
  }

  @Override
  public void waitForConnect() throws Exception {
    Thread.sleep(500L);

    myDebuggerStatusHolder.onConnecting();

    if (!myDebuggerStatusHolder.awaitWhileConnecting()) {
      throw new PyDebuggerException("The process terminated before IDE established connection with Python debugger script");
    }
  }

  @Override
  public void close() {
    myDebuggerStatusHolder.onDisconnectionInitiated();

    for (ProcessDebugger d : allDebuggers()) {
      d.close();
    }
  }

  private List<RemoteDebugger> allDebuggers() {
    synchronized (myDebuggersObject) {
      return new ArrayList<>(myDebuggers);
    }
  }

  @Override
  public void disconnect() {
    myDebuggerStatusHolder.onDisconnectionInitiated();

    for (ProcessDebugger d : allDebuggers()) {
      d.disconnect();
    }
  }

  @Override
  public String handshake() throws PyDebuggerException {
    synchronized (myDebuggersObject) {
      if (!myDebuggers.isEmpty()) {
        return myDebuggers.get(0).handshake();
      }
      else {
        throw new IllegalStateException("waitForConnect() must be executed before handshake()");
      }
    }
  }

  @Override
  public PyDebugValue evaluate(String threadId, String frameId, String expression, boolean execute, int evaluationTimeout)
    throws PyDebuggerException {
    return debugger(threadId).evaluate(threadId, frameId, expression, execute, evaluationTimeout);
  }

  @Override
  public PyDebugValue evaluate(String threadId,
                               String frameId,
                               String expression,
                               boolean execute,
                               int evaluationTimeout,
                               boolean trimResult)
    throws PyDebuggerException {
    return debugger(threadId).evaluate(threadId, frameId, expression, execute, evaluationTimeout, trimResult);
  }

  @Override
  public void consoleExec(String threadId, String frameId, String expression, PyDebugCallback<String> callback) {
    debugger(threadId).consoleExec(threadId, frameId, expression, callback);
  }

  @Override
  public XValueChildrenList loadFrame(String threadId, String frameId, GROUP_TYPE groupType) throws PyDebuggerException {
    return debugger(threadId).loadFrame(threadId, frameId, groupType);
  }

  @Override
  public @Nullable String execTableCommand(String threadId,
                                           String frameId,
                                           String command,
                                           TableCommandType commandType, TableCommandParameters tableCommandParameters)
    throws PyDebuggerException {
    return debugger(threadId).execTableCommand(threadId, frameId, command, commandType, tableCommandParameters);
  }

  @Override
  public @Nullable String execTableImageCommand(String threadId,
                                           String frameId,
                                           String command,
                                           TableCommandType commandType, TableCommandParameters tableCommandParameters)
    throws PyDebuggerException {
    return debugger(threadId).execTableCommand(threadId, frameId, command, commandType, tableCommandParameters);
  }

  @Override
  public List<Pair<String, Boolean>> getSmartStepIntoVariants(String threadId, String frameId, int startContextLine, int endContextLine)
    throws PyDebuggerException {
    return debugger(threadId).getSmartStepIntoVariants(threadId, frameId, startContextLine, endContextLine);
  }

  @Override
  public XValueChildrenList loadVariable(String threadId, String frameId, PyDebugValue var) throws PyDebuggerException {
    return debugger(threadId).loadVariable(threadId, frameId, var);
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
    return debugger(threadId).loadArrayItems(threadId, frameId, var, rowOffset, colOffset, rows, cols, format);
  }

  @Override
  public @NotNull DataViewerCommandResult executeDataViewerCommand(@NotNull DataViewerCommandBuilder builder) throws PyDebuggerException {
    assert builder.getThreadId() != null;
    return debugger(builder.getThreadId()).executeDataViewerCommand(builder);
  }

  @Override
  public void loadReferrers(String threadId,
                            String frameId,
                            PyReferringObjectsValue var,
                            PyDebugCallback<? super XValueChildrenList> callback) {
    debugger(threadId).loadReferrers(threadId, frameId, var, callback);
  }

  private @NotNull ProcessDebugger debugger(@NotNull String threadId) {
    ProcessDebugger debugger = myThreadRegistry.getDebugger(threadId);
    if (debugger != null) {
      return debugger;
    }
    else {
      // thread is not found in registry - lets search for it in attached debuggers

      List<RemoteDebugger> debuggers = getDebuggers();
      for (ProcessDebugger d : debuggers) {
        for (PyThreadInfo thread : d.getThreads()) {
          if (threadId.equals(thread.getId())) {
            return d;
          }
        }
      }

      //if not found then return main debugger
      return debuggers.get(0);
    }
  }

  @Override
  public PyDebugValue changeVariable(String threadId, String frameId, PyDebugValue var, String value) throws PyDebuggerException {
    return debugger(threadId).changeVariable(threadId, frameId, var, value);
  }

  @Override
  public void loadFullVariableValues(@NotNull String threadId,
                                     @NotNull String frameId,
                                     @NotNull List<PyFrameAccessor.PyAsyncValue<String>> vars) throws PyDebuggerException {
    debugger(threadId).loadFullVariableValues(threadId, frameId, vars);
  }

  @Override
  public String loadSource(String path) {
    synchronized (myDebuggersObject) {
      if (!myDebuggers.isEmpty()) {
        return myDebuggers.get(0).loadSource(path);
      }
      else {
        throw new IllegalStateException("waitForConnect() must be executed before loadSource()");
      }
    }
  }


  private static class ThreadRegistry {
    private final Map<String, RemoteDebugger> myThreadIdToDebugger = Maps.newHashMap();

    public void register(String id, RemoteDebugger debugger) {
      myThreadIdToDebugger.put(id, debugger);
    }

    public RemoteDebugger getDebugger(String threadId) {
      return myThreadIdToDebugger.get(threadId);
    }

    public static String threadName(@NotNull String name, @NotNull String id) {
      int indx = id.indexOf("_", id.indexOf("_") + 1);
      if (indx != -1) {
        id = id.substring(0, indx);
      }

      return name + "(" + id + ")";
    }
  }

  @Override
  public Collection<PyThreadInfo> getThreads() {
    cleanDebuggers();

    List<PyThreadInfo> threads = collectAllThreads();

    if (!isDebuggersEmpty()) {
      //here we add process id to thread name in case there are more then one process
      return Collections.unmodifiableCollection(Collections2.transform(threads, t -> {
        if (t == null) return null;
        String threadName = ThreadRegistry.threadName(t.getName(), t.getId());
        PyThreadInfo newThread =
          new PyThreadInfo(t.getId(), threadName, t.getFrames(),
                           t.getStopReason(),
                           t.getMessage());
        newThread.updateState(t.getState(), t.getFrames());
        return newThread;
      }));
    }
    else {
      return Collections.unmodifiableCollection(threads);
    }
  }

  private List<PyThreadInfo> collectAllThreads() {
    List<PyThreadInfo> result = new ArrayList<>();

    //collect threads and add them to registry to faster access
    //we don't register mainDebugger as it is default if there is no mapping
    for (RemoteDebugger d : myDebuggers) {
      result.addAll(d.getThreads());
      for (PyThreadInfo t : d.getThreads()) {
        myThreadRegistry.register(t.getId(), d);
      }
    }

    return result;
  }

  private void cleanDebuggers() {
    removeDisconnected(getDebuggers());
  }

  private void removeDisconnected(ArrayList<RemoteDebugger> debuggers) {
    boolean allConnected = true;
    for (RemoteDebugger d : debuggers) {
      if (!d.isConnected()) {
        allConnected = false;
      }
    }
    if (!allConnected) {
      List<RemoteDebugger> newList = new ArrayList<>();
      for (RemoteDebugger d : debuggers) {
        if (d.isConnected()) {
          newList.add(d);
        }
      }

      synchronized (myDebuggersObject) {
        myDebuggers.clear();
        myDebuggers.addAll(newList);
      }
    }
  }

  private ArrayList<RemoteDebugger> getDebuggers() {
    synchronized (myDebuggersObject) {
      return Lists.newArrayList(myDebuggers);
    }
  }

  private boolean isDebuggersEmpty() {
    synchronized (myDebuggersObject) {
      return myDebuggers.isEmpty();
    }
  }

  @Override
  public void execute(@NotNull AbstractCommand command) {
    for (ProcessDebugger d : allDebuggers()) {
      d.execute(command);
    }
  }

  @Override
  public void suspendAllThreads() {
    for (ProcessDebugger d : allDebuggers()) {
      d.suspendAllThreads();
    }
  }

  @Override
  public void suspendThread(String threadId) {
    debugger(threadId).suspendThread(threadId);
  }

  @Override
  public void run() throws PyDebuggerException {
    synchronized (myDebuggersObject) {
      if (!myDebuggers.isEmpty()) {
        myDebuggers.get(0).run();
      }
      else {
        throw new IllegalStateException("waitForConnect() must be executed before run()");
      }
    }
  }

  @Override
  public void smartStepInto(String threadId, String frameId, String functionName, int callOrder, int contextStartLine, int contextEndLine) {
    debugger(threadId).smartStepInto(threadId, frameId, functionName, callOrder, contextStartLine, contextEndLine);
  }

  @Override
  public void resumeOrStep(String threadId, ResumeOrStepCommand.Mode mode) {
    debugger(threadId).resumeOrStep(threadId, mode);
  }

  @Override
  public void setNextStatement(@NotNull String threadId,
                               @NotNull XSourcePosition sourcePosition,
                               @Nullable String functionName,
                               @NotNull PyDebugCallback<Pair<Boolean, String>> callback) {
    debugger(threadId).setNextStatement(threadId, sourcePosition, functionName, callback);
  }

  @Override
  public void setTempBreakpoint(@NotNull String type, @NotNull String file, int line) {
    for (ProcessDebugger d : allDebuggers()) {
      d.setTempBreakpoint(type, file, line);
    }
  }

  @Override
  public void removeTempBreakpoint(@NotNull String file, int line) {
    for (ProcessDebugger d : allDebuggers()) {
      d.removeTempBreakpoint(file, line);
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
    for (ProcessDebugger d : allDebuggers()) {
      d.setBreakpoint(typeId, file, line, condition, logExpression, funcName, policy);
    }
  }

  @Override
  public void removeBreakpoint(@NotNull String typeId, @NotNull String file, int line) {
    allDebuggers().forEach(d -> d.removeBreakpoint(typeId, file, line));
  }

  @Override
  public void setUserTypeRenderers(@NotNull List<@NotNull PyUserTypeRenderer> renderers) {
    allDebuggers().forEach(d -> d.setUserTypeRenderers(renderers));
  }

  @Override
  public void setShowReturnValues(boolean isShowReturnValues) {
    allDebuggers().forEach(d -> d.setShowReturnValues(isShowReturnValues));
  }

  @Override
  public void setUnitTestDebuggingMode() {
    allDebuggers().forEach(d -> d.setUnitTestDebuggingMode());
  }

  /**
   * Stores {@link RemoteDebuggerCloseListener} into the single composite
   * listener. This composite listener is later added to the first connected
   * {@link RemoteDebugger}.
   *
   * @param listener the listener to be added
   */
  @Override
  public void addCloseListener(RemoteDebuggerCloseListener listener) {
    myCompositeListener.addCloseListener(listener);
  }

  @Override
  public List<PydevCompletionVariant> getCompletions(String threadId, String frameId, String prefix) {
    return debugger(threadId).getCompletions(threadId, frameId, prefix);
  }

  @Override
  public String getDescription(String threadId, String frameId, String cmd) {
    return debugger(threadId).getDescription(threadId, frameId, cmd);
  }

  @Override
  public void addExceptionBreakpoint(ExceptionBreakpointCommandFactory factory) {
    for (RemoteDebugger d : allDebuggers()) {
      d.execute(factory.createAddCommand(d));
    }
  }

  @Override
  public void removeExceptionBreakpoint(ExceptionBreakpointCommandFactory factory) {
    for (RemoteDebugger d : allDebuggers()) {
      d.execute(factory.createRemoveCommand(d));
    }
  }

  @Override
  public void suspendOtherThreads(PyThreadInfo thread) {
    for (RemoteDebugger d : allDebuggers()) {
      // we should notify the debugger in each process about suspending all threads
      d.suspendOtherThreads(thread);
    }
  }

  private static class CompositeRemoteDebuggerCloseListener implements RemoteDebuggerCloseListener {
    private final List<RemoteDebuggerCloseListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

    public void addCloseListener(RemoteDebuggerCloseListener listener) {
      myListeners.add(listener);
    }

    @Override
    public void closed() {
      myListeners.forEach(RemoteDebuggerCloseListener::closed);
    }

    @Override
    public void communicationError() {
      myListeners.forEach(RemoteDebuggerCloseListener::communicationError);
    }

    @Override
    public void detached() {
      myListeners.forEach(RemoteDebuggerCloseListener::detached);
    }
  }

  @Override
  public void interruptDebugConsole() {
    for (RemoteDebugger debugger : myDebuggers) {
      debugger.interruptDebugConsole();
    }
  }
}
