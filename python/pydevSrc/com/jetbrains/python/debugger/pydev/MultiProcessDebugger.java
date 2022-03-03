// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger.pydev;

import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.execution.ExecutionException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.SuspendPolicy;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.jetbrains.python.console.pydev.PydevCompletionVariant;
import com.jetbrains.python.debugger.*;
import com.jetbrains.python.debugger.pydev.dataviewer.DataViewerCommandBuilder;
import com.jetbrains.python.debugger.pydev.dataviewer.DataViewerCommandResult;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

public class MultiProcessDebugger implements ProcessDebugger {
  private static final Logger LOG = Logger.getInstance(MultiProcessDebugger.class);

  private final IPyDebugProcess myDebugProcess;
  private final ServerSocket myServerSocket;
  private final int myTimeoutInMillis;

  private final RemoteDebugger myMainDebugger;
  private final List<RemoteDebugger> myOtherDebuggers = new ArrayList<>();
  /**
   * @deprecated the dispatcher code must be removed if no issues arise in Python debugger
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.1")
  private final @Nullable ServerSocket myDebugServerSocket;
  private DebuggerProcessAcceptor myDebugProcessAcceptor;
  private final List<DebuggerProcessListener> myOtherDebuggerCloseListener = new ArrayList<>();

  private final ThreadRegistry myThreadRegistry = new ThreadRegistry();

  public MultiProcessDebugger(@NotNull final IPyDebugProcess debugProcess,
                              @NotNull final ServerSocket serverSocket,
                              final int timeoutInMillis) {
    this(debugProcess, serverSocket, timeoutInMillis, false);
  }

  /**
   * @deprecated the dispatcher code must be removed if no issues arise in Python debugger
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.1")
  public MultiProcessDebugger(@NotNull final IPyDebugProcess debugProcess,
                              @NotNull final ServerSocket serverSocket,
                              final int timeoutInMillis,
                              boolean useDispatcher) {
    myDebugProcess = debugProcess;

    myServerSocket = serverSocket;
    myTimeoutInMillis = timeoutInMillis;

    ServerSocket debugServerSocket;
    if (useDispatcher) {
      try {
        debugServerSocket = createServerSocket();
        myDebugServerSocket = debugServerSocket;
      }
      catch (ExecutionException e) {
        throw new RuntimeException("Failed to start debugger", e);
      }
    }
    else {
      debugServerSocket = serverSocket;
      myDebugServerSocket = null;
    }
    myMainDebugger = new RemoteDebugger(myDebugProcess, debugServerSocket, myTimeoutInMillis);
  }

  @Override
  public boolean isConnected() {
    return myMainDebugger.isConnected();
  }

  @Override
  public void waitForConnect() throws Exception {
    if (myDebugServerSocket != null) {
      //noinspection SocketOpenedButNotSafelyClosed
      final Socket socket = myServerSocket.accept();

      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        try {
          //do we need any synchronization here with myMainDebugger.waitForConnect() ??? TODO
          sendDebuggerPort(socket, myDebugServerSocket, myDebugProcess);
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
      });
    }

    myMainDebugger.waitForConnect();

    disposeAcceptor();

    myDebugProcessAcceptor = new DebuggerProcessAcceptor(this, myServerSocket);
    ApplicationManager.getApplication().executeOnPooledThread(myDebugProcessAcceptor);
  }

  /**
   * @deprecated the dispatcher code must be removed if no issues arise in Python debugger
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.1")
  private static void sendDebuggerPort(@NotNull Socket socket, @NotNull ServerSocket serverSocket, @NotNull IPyDebugProcess processHandler)
    throws IOException {
    int port = processHandler.handleDebugPort(serverSocket.getLocalPort());
    PrintWriter writer = new PrintWriter(socket.getOutputStream());
    try {
      writer.println(99 + "\t" + -1 + "\t" + port);
      writer.flush();
    }
    finally {
      socket.close();
      writer.close();
    }
  }

  /**
   * @deprecated the dispatcher code must be removed if no issues arise in Python debugger
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.1")
  @NotNull
  private static ServerSocket createServerSocket() throws ExecutionException {
    final ServerSocket serverSocket;
    try {
      //noinspection IOResourceOpenedButNotSafelyClosed,SocketOpenedButNotSafelyClosed
      serverSocket = new ServerSocket(0);
    }
    catch (IOException e) {
      throw new ExecutionException("Failed to find free socket port", e); //NON-NLS
    }
    return serverSocket;
  }

  /**
   * @deprecated the dispatcher code must be removed if no issues arise in Python debugger
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.1")
  private boolean useDispatcher() {
    return myDebugServerSocket != null;
  }

  @Override
  public void close() {
    for (ProcessDebugger d : allDebuggers()) {
      d.close();
    }
    disposeAcceptor();

    if (!myServerSocket.isClosed()) {
      try {
        myServerSocket.close();
      }
      catch (IOException e) {
        LOG.warn("Error closing socket", e);
      }
    }
  }

  private List<RemoteDebugger> allDebuggers() {
    List<RemoteDebugger> result = Lists.newArrayList(myMainDebugger);
    synchronized (myOtherDebuggers) {
      result.addAll(myOtherDebuggers);
    }
    return result;
  }

  @Override
  public void disconnect() {
    for (ProcessDebugger d : allDebuggers()) {
      d.disconnect();
    }
    disposeAcceptor();
  }

  private void disposeAcceptor() {
    if (myDebugProcessAcceptor != null) {
      myDebugProcessAcceptor.disconnect();
      myDebugProcessAcceptor = null;
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
  public @Nullable String execTableCommand(String threadId,
                                           String frameId,
                                           String command,
                                           TableCommandType commandType) throws PyDebuggerException {
    return debugger(threadId).execTableCommand(threadId, frameId, command, commandType);
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
  @NotNull
  public DataViewerCommandResult executeDataViewerCommand(@NotNull DataViewerCommandBuilder builder) throws PyDebuggerException {
    assert builder.getThreadId() != null;
    return debugger(builder.getThreadId()).executeDataViewerCommand(builder);
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

      for (ProcessDebugger d : myOtherDebuggers) {
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
  public void loadFullVariableValues(@NotNull String threadId,
                                     @NotNull String frameId,
                                     @NotNull List<PyFrameAccessor.PyAsyncValue<String>> vars) throws PyDebuggerException {
    debugger(threadId).loadFullVariableValues(threadId, frameId, vars);
  }

  @Override
  public String loadSource(String path) {
    return myMainDebugger.loadSource(path);
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
    cleanOtherDebuggers();

    List<PyThreadInfo> threads = collectAllThreads();

    if (myOtherDebuggers.size() > 0) {
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
    synchronized (myOtherDebuggers) {
      removeDisconnected(getOtherDebuggers());
    }
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

      synchronized (myOtherDebuggers) {
        myOtherDebuggers.clear();
        myOtherDebuggers.addAll(newList);
      }
    }
  }

  private ArrayList<RemoteDebugger> getOtherDebuggers() {
    synchronized (myOtherDebuggers) {
      return Lists.newArrayList(myOtherDebuggers);
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
    myMainDebugger.run();
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
  public void setShowReturnValues(boolean isShowReturnValues) {
    allDebuggers().forEach(d -> d.setShowReturnValues(isShowReturnValues));
  }

  @Override
  public void setUnitTestDebuggingMode() {
    allDebuggers().forEach(d -> d.setUnitTestDebuggingMode());
  }

  private static class DebuggerProcessAcceptor implements Runnable {
    private volatile boolean myShouldAccept = true;
    private final MultiProcessDebugger myMultiProcessDebugger;
    private ServerSocket myServerSocket;

    DebuggerProcessAcceptor(@NotNull MultiProcessDebugger multiProcessDebugger, @NotNull ServerSocket serverSocket) {
      myMultiProcessDebugger = multiProcessDebugger;
      myServerSocket = serverSocket;
    }

    @Override
    public void run() {
      while (myShouldAccept) {
        try {
          final ServerSocket serverSocketCopy = myServerSocket;
          if (serverSocketCopy == null) {
            break;
          }
          Socket socket;
          if (myMultiProcessDebugger.useDispatcher()) {
            socket = serverSocketCopy.accept();
          }
          else {
            socket = null;
          }

          try {
            final ServerSocket serverSocket;
            if (socket != null) {
              serverSocket = createServerSocket();
            }
            else {
              serverSocket = serverSocketCopy;
            }
            final RemoteDebugger debugger =
              new RemoteDebugger(myMultiProcessDebugger.myDebugProcess, serverSocket, myMultiProcessDebugger.myTimeoutInMillis);
            addCloseListener(debugger);
            if (socket != null) {
              sendDebuggerPort(socket, serverSocket, myMultiProcessDebugger.myDebugProcess);
              socket.close();
            }
            debugger.waitForConnect();
            debugger.handshake();
            myMultiProcessDebugger.addDebugger(debugger);
            myMultiProcessDebugger.myDebugProcess.init();

            debugger.run();
          }
          catch (Exception e) {
            LOG.warn(e);
          }
          finally {
            if (socket != null && !socket.isClosed()) {
              socket.close();
            }
          }
        }
        catch (Exception ignore) {
          if (myServerSocket == null) {
            myShouldAccept = false;
          }
        }
      }
    }

    private void addCloseListener(@NotNull final RemoteDebugger debugger) {
      debugger.addCloseListener(new RemoteDebuggerCloseListener() {
        @Override
        public void closed() {
          notifyThreadsClosed(debugger);
        }

        @Override
        public void communicationError() {
          notifyThreadsClosed(debugger);
        }

        @Override
        public void detached() {
          notifyThreadsClosed(debugger);
        }
      });
    }

    private void notifyThreadsClosed(RemoteDebugger debugger) {
      for (DebuggerProcessListener l : myMultiProcessDebugger.myOtherDebuggerCloseListener) {
        l.threadsClosed(collectThreads(debugger));
      }
    }

    private Set<String> collectThreads(RemoteDebugger debugger) {
      Set<String> result = new HashSet<>();
      for (Map.Entry<String, RemoteDebugger> entry : myMultiProcessDebugger.myThreadRegistry.myThreadIdToDebugger.entrySet()) {
        if (entry.getValue() == debugger) {
          result.add(entry.getKey());
        }
      }
      return result;
    }

    public void disconnect() {
      myShouldAccept = false;
      if (myServerSocket != null && !myServerSocket.isClosed()) {
        try {
          myServerSocket.close();
        }
        catch (IOException ignore) {
        }
        myServerSocket = null;
      }
    }
  }

  private void addDebugger(RemoteDebugger debugger) {
    synchronized (myOtherDebuggers) {
      myOtherDebuggers.add(debugger);
    }
  }

  @Override
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

  public void removeCloseListener(RemoteDebuggerCloseListener listener) {
    myMainDebugger.removeCloseListener(listener);
  }

  public void addOtherDebuggerCloseListener(DebuggerProcessListener otherDebuggerCloseListener) {
    myOtherDebuggerCloseListener.add(otherDebuggerCloseListener);
  }

  public interface DebuggerProcessListener {
    void threadsClosed(Set<String> threadIds);
  }

  @Override
  public void interruptDebugConsole() {
    for (RemoteDebugger d : allDebuggers()) {
      d.interruptDebugConsole();
    }
  }
}
