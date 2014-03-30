package com.jetbrains.python.debugger.pydev;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.execution.ExecutionException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.jetbrains.python.console.pydev.PydevCompletionVariant;
import com.jetbrains.python.debugger.IPyDebugProcess;
import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.PyDebuggerException;
import com.jetbrains.python.debugger.PyThreadInfo;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

/**
 * @author traff
 */
public class MultiProcessDebugger implements ProcessDebugger {
  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.pydev.remote.MultiProcessDebugger");

  private final IPyDebugProcess myDebugProcess;
  private final ServerSocket myServerSocket;
  private final int myTimeoutInMillis;

  private RemoteDebugger myMainDebugger;
  private final List<RemoteDebugger> myOtherDebuggers = Lists.newArrayList();
  private ServerSocket myDebugServerSocket;
  private DebuggerProcessAcceptor myDebugProcessAcceptor;
  private List<DebuggerProcessListener> myOtherDebuggerCloseListener = Lists.newArrayList();

  private ThreadRegistry myThreadRegistry = new ThreadRegistry();

  public MultiProcessDebugger(@NotNull final IPyDebugProcess debugProcess,
                              @NotNull final ServerSocket serverSocket,
                              final int timeoutInMillis) {
    myDebugProcess = debugProcess;

    myServerSocket = serverSocket;
    myTimeoutInMillis = timeoutInMillis;

    try {
      myDebugServerSocket = createServerSocket();
    }
    catch (ExecutionException e) {
    }
    myMainDebugger = new RemoteDebugger(myDebugProcess, myDebugServerSocket, myTimeoutInMillis);
  }

  @Override
  public boolean isConnected() {
    return myMainDebugger.isConnected();
  }

  @Override
  public void waitForConnect() throws Exception {
    try {
      //noinspection SocketOpenedButNotSafelyClosed
      final Socket socket = myServerSocket.accept();

      ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
        @Override
        public void run() {
          try {
            //do we need any synchronization here with myMainDebugger.waitForConnect() ??? TODO
            sendDebuggerPort(socket, myDebugServerSocket, myDebugProcess);
          }
          catch (Exception e) {
            throw new RuntimeException(e);
          }
        }
      });

      myMainDebugger.waitForConnect();


      disposeAcceptor();

      myDebugProcessAcceptor = new DebuggerProcessAcceptor(this, myServerSocket);
      ApplicationManager.getApplication().executeOnPooledThread(myDebugProcessAcceptor);
    }
    finally {

    }
  }

  private static void sendDebuggerPort(Socket socket, ServerSocket serverSocket, IPyDebugProcess processHandler) throws IOException {
    int port = processHandler.handleDebugPort(serverSocket.getLocalPort());
    PrintWriter writer = new PrintWriter(socket.getOutputStream());
    writer.println(99 + "\t" + -1 + "\t" + port);
    writer.flush();
    socket.close();
  }

  private static ServerSocket createServerSocket() throws ExecutionException {
    final ServerSocket serverSocket;
    try {
      //noinspection SocketOpenedButNotSafelyClosed
      serverSocket = new ServerSocket(0);
    }
    catch (IOException e) {
      throw new ExecutionException("Failed to find free socket port", e);
    }
    return serverSocket;
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
  public void consoleExec(String threadId, String frameId, String expression, DebugCallback<String> callback) {
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

  @NotNull
  private ProcessDebugger debugger(@NotNull String threadId) {
    ProcessDebugger debugger = myThreadRegistry.getDebugger(threadId);
    if (debugger != null) {
      return debugger;
    }
    else {
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
      int indx = id.indexOf("_");
      if (indx != -1) {
        id = id.substring(0, indx);
      }

      return name + "(" + id + ")";
    }
  }

  @Override
  public Collection<PyThreadInfo> getThreads() {
    List<PyThreadInfo> threads = Lists.newArrayList(myMainDebugger.getThreads());

    List<PyThreadInfo> result = Lists.newArrayList();

    cleanOtherDebuggers();

    collectAndRegisterOtherDebuggersThreads(threads); //we don't register mainDebugger as it is default if there is no mapping


    if (myOtherDebuggers.size() > 0) {
      //here we add process id to thread name in case there are more then one process
      threads = addProcessIdToThreadName(threads, result);
    }

    return Collections.unmodifiableCollection(threads);
  }

  private static List<PyThreadInfo> addProcessIdToThreadName(List<PyThreadInfo> threads, List<PyThreadInfo> result) {
    for (PyThreadInfo t : threads) {
      String threadName = ThreadRegistry.threadName(t.getName(), t.getId());
      PyThreadInfo newThread =
        new PyThreadInfo(t.getId(), threadName, t.getFrames(),
                         t.getStopReason(),
                         t.getMessage());
      newThread.updateState(t.getState(), t.getFrames());
      result.add(newThread);
    }
    threads = result;
    return threads;
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
      List<RemoteDebugger> newList = Lists.newArrayList();
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

  private void collectAndRegisterOtherDebuggersThreads(List<PyThreadInfo> threads) {
    for (RemoteDebugger d : getOtherDebuggers()) {
      threads.addAll(d.getThreads());
      for (PyThreadInfo t : d.getThreads()) {
        myThreadRegistry.register(t.getId(), d);
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
  public void smartStepInto(String threadId, String functionName) {
    debugger(threadId).smartStepInto(threadId, functionName);
  }

  @Override
  public void resumeOrStep(String threadId, ResumeOrStepCommand.Mode mode) {
    debugger(threadId).resumeOrStep(threadId, mode);
  }

  @Override
  public void setTempBreakpoint(String type, String file, int line) {
    for (ProcessDebugger d : allDebuggers()) {
      d.setTempBreakpoint(type, file, line);
    }
  }

  @Override
  public void removeTempBreakpoint(String file, int line) {
    for (ProcessDebugger d : allDebuggers()) {
      d.removeTempBreakpoint(file, line);
    }
  }

  @Override
  public void setBreakpoint(String typeId, String file, int line, String condition, String logExpression) {
    for (ProcessDebugger d : allDebuggers()) {
      d.setBreakpoint(typeId, file, line, condition, logExpression);
    }
  }

  @Override
  public void removeBreakpoint(String typeId, String file, int line) {
    for (ProcessDebugger d : allDebuggers()) {
      d.removeBreakpoint(typeId, file, line);
    }
  }

  private static class DebuggerProcessAcceptor implements Runnable {
    private volatile boolean myShouldAccept = true;
    private final MultiProcessDebugger myMultiProcessDebugger;
    private ServerSocket myServerSocket;

    public DebuggerProcessAcceptor(@NotNull MultiProcessDebugger multiProcessDebugger, @NotNull ServerSocket serverSocket) {
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
          Socket socket = serverSocketCopy.accept();

          try {
            final ServerSocket serverSocket = createServerSocket();
            final RemoteDebugger debugger =
              new RemoteDebugger(myMultiProcessDebugger.myDebugProcess, serverSocket, myMultiProcessDebugger.myTimeoutInMillis);
            addCloseListener(debugger);
            sendDebuggerPort(socket, serverSocket, myMultiProcessDebugger.myDebugProcess);
            socket.close();
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
            if (!socket.isClosed()) {
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
      Set<String> result = Sets.newHashSet();
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

  public void addCloseListener(RemoteDebuggerCloseListener listener) {
    myMainDebugger.addCloseListener(listener);
  }

  @Override
  public List<PydevCompletionVariant> getCompletions(String threadId, String frameId, String prefix) {
    return debugger(threadId).getCompletions(threadId, frameId, prefix);
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

  public void removeCloseListener(RemoteDebuggerCloseListener listener) {
    myMainDebugger.removeCloseListener(listener);
  }

  public void addOtherDebuggerCloseListener(DebuggerProcessListener otherDebuggerCloseListener) {
    myOtherDebuggerCloseListener.add(otherDebuggerCloseListener);
  }

  public interface DebuggerProcessListener {
    void threadsClosed(Set<String> threadIds);
  }
}
