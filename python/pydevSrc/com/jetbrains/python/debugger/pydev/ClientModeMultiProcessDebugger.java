package com.jetbrains.python.debugger.pydev;

import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.xdebugger.breakpoints.SuspendPolicy;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.jetbrains.python.console.pydev.PydevCompletionVariant;
import com.jetbrains.python.debugger.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author traff
 */
public class ClientModeMultiProcessDebugger implements ProcessDebugger {
  private static final Logger LOG = Logger.getInstance(ClientModeMultiProcessDebugger.class);

  private final IPyDebugProcess myDebugProcess;
  @NotNull private final String myHost;
  private final int myPort;

  @NotNull private final RemoteDebugger myMainDebugger;
  private final Object myOtherDebuggersObject = new Object();
  private final List<RemoteDebugger> myOtherDebuggers = Lists.newArrayList();

  private ThreadRegistry myThreadRegistry = new ThreadRegistry();

  public ClientModeMultiProcessDebugger(@NotNull final IPyDebugProcess debugProcess,
                                        @NotNull String host, int port) {
    myDebugProcess = debugProcess;
    myHost = host;
    myPort = port;

    myMainDebugger = createDebugger();

    myOtherDebuggers.add(myMainDebugger);
  }

  @NotNull
  private RemoteDebugger createDebugger() {
    return new RemoteDebugger(myDebugProcess, myHost, myPort) {
      @Override
      protected void onProcessCreatedEvent() throws PyDebuggerException {
        ApplicationManager.getApplication().executeOnPooledThread(ClientModeMultiProcessDebugger.this::connectToSubprocess);
      }
    };
  }

  @Override
  public boolean isConnected() {
    return getOtherDebuggers().stream().anyMatch(RemoteDebugger::isConnected);
  }

  @Override
  public void waitForConnect() throws Exception {
    Thread.sleep(500L);

    myMainDebugger.waitForConnect();
  }

  private void connectToSubprocess() {
    try {
      RemoteDebugger debugger = createDebugger();
      debugger.waitForConnect();

      addDebugger(debugger);

      myDebugProcess.init();
      debugger.run();

      return;
    }
    catch (RuntimeException e) {
      LOG.warn(e);
    }
    catch (Exception ignored) {
    }
    try {
      //noinspection BusyWait
      Thread.sleep(50L);
    }
    catch (InterruptedException ignored) {
    }
    LOG.debug("Could not connect to subprocess");
  }

  @Override
  public void close() {
    for (ProcessDebugger d : allDebuggers()) {
      d.close();
    }
  }

  private List<RemoteDebugger> allDebuggers() {
    List<RemoteDebugger> result = new ArrayList<>();
    synchronized (myOtherDebuggersObject) {
      result.addAll(myOtherDebuggers);
    }
    return result;
  }

  @Override
  public void disconnect() {
    for (ProcessDebugger d : allDebuggers()) {
      d.disconnect();
    }
  }

  @Override
  public String handshake() throws PyDebuggerException {
    return myMainDebugger.handshake();
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

      for (ProcessDebugger d : getOtherDebuggers()) {
        for (PyThreadInfo thread : d.getThreads()) {
          if (threadId.equals(thread.getId())) {
            return d;
          }
        }
      }

      //if not found then return main debugger
      return myMainDebugger;
    }
  }

  @Override
  public PyDebugValue changeVariable(String threadId, String frameId, PyDebugValue var, String value) throws PyDebuggerException {
    return debugger(threadId).changeVariable(threadId, frameId, var, value);
  }

  @Override
  public String loadSource(String path) {
    return myMainDebugger.loadSource(path);
  }


  private static class ThreadRegistry {
    private Map<String, RemoteDebugger> myThreadIdToDebugger = Maps.newHashMap();

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
    cleanOtherDebuggers();

    List<PyThreadInfo> threads = collectAllThreads();

    if (!isOtherDebuggersEmpty()) {
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

    result.addAll(myMainDebugger.getThreads());

    //collect threads and add them to registry to faster access
    //we don't register mainDebugger as it is default if there is no mapping
    for (RemoteDebugger d : myOtherDebuggers) {
      result.addAll(d.getThreads());
      for (PyThreadInfo t : d.getThreads()) {
        myThreadRegistry.register(t.getId(), d);
      }
    }

    return result;
  }

  private void cleanOtherDebuggers() {
    removeDisconnected(getOtherDebuggers());
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

      synchronized (myOtherDebuggersObject) {
        myOtherDebuggers.clear();
        myOtherDebuggers.addAll(newList);
      }
    }
  }

  private ArrayList<RemoteDebugger> getOtherDebuggers() {
    synchronized (myOtherDebuggersObject) {
      return Lists.newArrayList(myOtherDebuggers);
    }
  }

  private boolean isOtherDebuggersEmpty() {
    synchronized (myOtherDebuggersObject) {
      return myOtherDebuggers.isEmpty();
    }
  }

  @Override
  public void execute(@NotNull AbstractCommand command, boolean waitForResult) {
    for (ProcessDebugger d : allDebuggers()) {
      d.execute(command, waitForResult);
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
    myMainDebugger.run();
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

  private void addDebugger(RemoteDebugger debugger) {
    synchronized (myOtherDebuggersObject) {
      myOtherDebuggers.add(debugger);
    }
  }

  public void addCloseListener(RemoteDebuggerCloseListener listener) {
    myMainDebugger.addCloseListener(listener);
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
      d.execute(factory.createAddCommand(d), false);
    }
  }

  @Override
  public void removeExceptionBreakpoint(ExceptionBreakpointCommandFactory factory) {
    for (RemoteDebugger d : allDebuggers()) {
      d.execute(factory.createRemoveCommand(d), false);
    }
  }

  @Override
  public void suspendOtherThreads(PyThreadInfo thread) {
    for (RemoteDebugger d : allDebuggers()) {
      // we should notify the debugger in each process about suspending all threads
      d.suspendOtherThreads(thread);
    }
  }

}
