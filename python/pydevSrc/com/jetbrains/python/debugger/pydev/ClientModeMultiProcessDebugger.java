package com.jetbrains.python.debugger.pydev;

import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.SuspendPolicy;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.jetbrains.python.console.pydev.PydevCompletionVariant;
import com.jetbrains.python.debugger.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @see com.jetbrains.python.debugger.pydev.transport.ClientModeDebuggerTransport
 */
public class ClientModeMultiProcessDebugger implements ProcessDebugger {
  private static final Logger LOG = Logger.getInstance(ClientModeMultiProcessDebugger.class);

  private final IPyDebugProcess myDebugProcess;
  @NotNull private final String myHost;
  private final int myPort;

  private final Object myDebuggersObject = new Object();
  /**
   * Guarded by {@link #myDebuggersObject}.
   */
  private final List<RemoteDebugger> myDebuggers = Lists.newArrayList();

  private final ThreadRegistry myThreadRegistry = new ThreadRegistry();

  /**
   * Indicates that this {@link ClientModeMultiProcessDebugger} has connected
   * at least to one (the main one) Python debugging process.
   * <p>
   * <i>As Python debugging process does not start the underlying Python script
   * before we send him {@link RunCommand} so the first process we connected to
   * is the main Python script process.</i>
   */
  private final AtomicBoolean myConnected = new AtomicBoolean(false);

  private final CountDownLatch myConnectedLatch = new CountDownLatch(1);

  private final CompositeRemoteDebuggerCloseListener myCompositeListener = new CompositeRemoteDebuggerCloseListener();

  private final RecurrentTaskExecutor<RemoteDebugger> myExecutor;

  public ClientModeMultiProcessDebugger(@NotNull final IPyDebugProcess debugProcess,
                                        @NotNull String host, int port) {
    myDebugProcess = debugProcess;
    myHost = host;
    myPort = port;

    String connectionThreadsName = "Debugger connection threads (" + host + ":" + port + ")";
    myExecutor = new RecurrentTaskExecutor<>(connectionThreadsName, this::tryToConnectRemoteDebugger, this::onRemoteDebuggerConnected);
  }

  /**
   * Should either successfully connect to the debugger script and return the
   * {@link RemoteDebugger} or throw {@link IOException} because of the
   * connection error or the socket timeout error.
   * <p>
   * We assume that debugger is successfully connected if the Python debugger
   * script responded on `CMD_VERSION` command (see
   * {@link RemoteDebugger#handshake()}).
   *
   * @return the successfully connected {@link RemoteDebugger}
   * @throws Exception if the connection or timeout error occurred
   * @see com.jetbrains.python.debugger.pydev.transport.ClientModeDebuggerTransport
   */
  @NotNull
  private RemoteDebugger tryToConnectRemoteDebugger() throws Exception {
    RemoteDebugger debugger = new RemoteDebugger(myDebugProcess, myHost, myPort) {
      @Override
      protected void onProcessCreatedEvent() {
        myExecutor.incrementRequests();
      }
    };
    debugger.waitForConnect();
    return debugger;
  }

  private void onRemoteDebuggerConnected(@NotNull RemoteDebugger debugger) {
    // register successfully connected debugger
    synchronized (myDebuggersObject) {
      myDebuggers.add(debugger);
    }

    // notify `waitForConnect()` that we connected
    if (myConnected.compareAndSet(false, true)) {
      // add close listeners for the first accepted debugger
      debugger.addCloseListener(myCompositeListener);

      // must be counted down only the first time
      myConnectedLatch.countDown();
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
    return allDebuggers().stream().anyMatch(RemoteDebugger::isConnected);
  }

  @Override
  public void waitForConnect() throws Exception {
    Thread.sleep(500L);

    // increment the number of debugger connection request initially
    myExecutor.incrementRequests();

    // waiting for the first connected thread
    myConnectedLatch.await(60, TimeUnit.SECONDS);
  }

  @Override
  public void close() {
    myExecutor.dispose();

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
    myExecutor.dispose();

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
  public PyDebugValue evaluate(String threadId, String frameId, String expression, boolean execute) throws PyDebuggerException {
    return debugger(threadId).evaluate(threadId, frameId, expression, execute);
  }

  @Override
  public PyDebugValue evaluate(String threadId, String frameId, String expression, boolean execute, boolean trimResult)
    throws PyDebuggerException {
    return debugger(threadId).evaluate(threadId, frameId, expression, execute, trimResult);
  }

  @Override
  public void consoleExec(String threadId, String frameId, String expression, PyDebugCallback<String> callback) {
    debugger(threadId).consoleExec(threadId, frameId, expression, callback);
  }

  @Override
  public XValueChildrenList loadFrame(String threadId, String frameId) throws PyDebuggerException {
    return debugger(threadId).loadFrame(threadId, frameId);
  }

  @Override
  public XValueChildrenList loadVariable(String threadId, String frameId, PyDebugValue var) throws PyDebuggerException {
    return debugger(threadId).loadVariable(threadId, frameId, var);
  }

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
  public void loadReferrers(String threadId, String frameId, PyReferringObjectsValue var, PyDebugCallback<XValueChildrenList> callback) {
    debugger(threadId).loadReferrers(threadId, frameId, var, callback);
  }

  @NotNull
  private ProcessDebugger debugger(@NotNull String threadId) {
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
    List<PyThreadInfo> result = Lists.newArrayList();

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
      List<RemoteDebugger> newList = Lists.newArrayList();
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
  public void smartStepInto(String threadId, String functionName) {
    debugger(threadId).smartStepInto(threadId, functionName);
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
    for (ProcessDebugger d : allDebuggers()) {
      d.removeBreakpoint(typeId, file, line);
    }
  }

  @Override
  public void setShowReturnValues(boolean isShowReturnValues) {
    for (ProcessDebugger d : allDebuggers()) {
      d.setShowReturnValues(isShowReturnValues);
    }
  }

  /**
   * Stores {@link RemoteDebuggerCloseListener} into the single composite
   * listener. This composite listener is later added to the first connected
   * {@link RemoteDebugger}.
   *
   * @param listener the listener to be added
   */
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
}
