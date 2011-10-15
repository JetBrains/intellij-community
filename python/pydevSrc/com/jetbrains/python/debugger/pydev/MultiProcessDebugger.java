package com.jetbrains.python.debugger.pydev;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.execution.ExecutionException;
import com.intellij.openapi.application.ApplicationManager;
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
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author traff
 */
public class MultiProcessDebugger implements ProcessDebugger {
  private final IPyDebugProcess myDebugProcess;
  private final ServerSocket myServerSocket;
  private final int myTimeout;

  private RemoteDebugger myMainDebugger;
  private List<RemoteDebugger> myOtherDebuggers = Lists.newArrayList();
  private ServerSocket myDebugServerSocket;

  public MultiProcessDebugger(final IPyDebugProcess debugProcess, final ServerSocket serverSocket, final int timeout) {
    myDebugProcess = debugProcess;
    myServerSocket = serverSocket;
    myTimeout = timeout * 1000;  // to milliseconds

    try {
      myDebugServerSocket = createServerSocket();
    }
    catch (ExecutionException e) {
    }
    myMainDebugger = new RemoteDebugger(myDebugProcess, myDebugServerSocket, myTimeout);
  }

  @Override
  public boolean isConnected() {
    return myMainDebugger.isConnected();
  }

  @Override
  public void waitForConnect() throws Exception {
    try {
      //noinspection SocketOpenedButNotSafelyClosed
      Socket socket = myServerSocket.accept();

      sendDebuggerPort(socket, myDebugServerSocket);
      myMainDebugger.waitForConnect();
      final DebuggerProcessAcceptor acceptor = new DebuggerProcessAcceptor(this, myServerSocket);
      ApplicationManager.getApplication().executeOnPooledThread(acceptor);
    }
    finally {

    }
  }

  private static void sendDebuggerPort(Socket socket, ServerSocket serverSocket) throws IOException {
    PrintWriter writer = new PrintWriter(socket.getOutputStream());
    writer.println(99 + "\t" + -1 + "\t" + serverSocket.getLocalPort());
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
  public void disconnect() {
    myMainDebugger.disconnect();
    for (ProcessDebugger d : myOtherDebuggers) {
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
  public String consoleExec(String threadId, String frameId, String expression) throws PyDebuggerException {
    return debugger(threadId).consoleExec(threadId, frameId, expression);
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
    private Map<String, ProcessDebugger> myThreadIdToDebugger = Maps.newHashMap();

    public void register(String id, ProcessDebugger debugger) {
      myThreadIdToDebugger.put(id, debugger);
    }

    public ProcessDebugger getDebugger(String threadId) {
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

  private ThreadRegistry myThreadRegistry = new ThreadRegistry();

  @Override
  public Collection<PyThreadInfo> getThreads() {
    List<PyThreadInfo> threads = Lists.newArrayList(myMainDebugger.getThreads());
    List<PyThreadInfo> result = Lists.newArrayList();

    cleanDebuggers();

    collectAndRegisterThreads(threads);

    for (PyThreadInfo t : threads) {
      myThreadRegistry.register(t.getId(), myMainDebugger);

      String threadName = myOtherDebuggers.size() > 0 ? ThreadRegistry.threadName(t.getName(), t.getId()) : t.getName();
      PyThreadInfo newThread =
        new PyThreadInfo(t.getId(), threadName, t.getFrames(),
                         t.getStopReason(),
                         t.getMessage());
      threads.add(newThread);
    }


    return Collections.unmodifiableCollection(result);
  }

  private void cleanDebuggers() {
    boolean allConnected = true;
    for (RemoteDebugger d : myOtherDebuggers) {
      if (!d.isConnected()) {
        allConnected = false;
      }
    }
    if (!allConnected) {
      List<RemoteDebugger> newList = Lists.newArrayList();
      for (RemoteDebugger d : myOtherDebuggers) {
        if (d.isConnected()) {
          newList.add(d);
        }
      }

      myOtherDebuggers = newList;
    }
  }

  private void collectAndRegisterThreads(List<PyThreadInfo> threads) {
    for (ProcessDebugger d : myOtherDebuggers) {
      threads.addAll(d.getThreads());
      for (PyThreadInfo t : d.getThreads()) {
        myThreadRegistry.register(t.getId(), d);
      }
    }
  }


  @Override
  public void execute(@NotNull AbstractCommand command) {
    myMainDebugger.execute(command);
    for (ProcessDebugger d : myOtherDebuggers) {
      d.execute(command);
    }
  }

  @Override
  public void suspendAllThreads() {
    myMainDebugger.suspendAllThreads();
    for (ProcessDebugger d : myOtherDebuggers) {
      d.suspendAllThreads();
    }
  }

  @Override
  public void suspendThread(String threadId) {
    debugger(threadId).suspendThread(threadId);
  }

  @Override
  public void close() {
    myMainDebugger.close();
    for (ProcessDebugger d : myOtherDebuggers) {
      d.close();
    }
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
    myMainDebugger.setTempBreakpoint(type, file, line);
    for (ProcessDebugger d : myOtherDebuggers) {
      d.setTempBreakpoint(type, file, line);
    }
  }

  @Override
  public void removeTempBreakpoint(String file, int line) {
    myMainDebugger.removeTempBreakpoint(file, line);
    for (ProcessDebugger d : myOtherDebuggers) {
      d.removeTempBreakpoint(file, line);
    }
  }

  @Override
  public void setBreakpoint(String typeId, String file, int line, String condition, String logExpression) {
    myMainDebugger.setBreakpoint(typeId, file, line, condition, logExpression);
    for (ProcessDebugger d : myOtherDebuggers) {
      d.setBreakpoint(typeId, file, line, condition, logExpression);
    }
  }

  @Override
  public void removeBreakpoint(String typeId, String file, int line) {
    myMainDebugger.removeBreakpoint(typeId, file, line);
    for (ProcessDebugger d : myOtherDebuggers) {
      d.removeBreakpoint(typeId, file, line);
    }
  }

  private static class DebuggerProcessAcceptor implements Runnable {
    private boolean myShouldAccept = true;
    private MultiProcessDebugger myMultiProcessDebugger;
    private ServerSocket myServerSocket;

    public DebuggerProcessAcceptor(MultiProcessDebugger multiProcessDebugger, ServerSocket serverSocket) {
      myMultiProcessDebugger = multiProcessDebugger;
      myServerSocket = serverSocket;
    }

    @Override
    public void run() {
      while (myShouldAccept) {
        try {
          Socket socket = myServerSocket.accept();

          final ServerSocket serverSocket = createServerSocket();
          RemoteDebugger debugger =
            new RemoteDebugger(myMultiProcessDebugger.myDebugProcess, serverSocket, myMultiProcessDebugger.myTimeout);
          sendDebuggerPort(socket, serverSocket);
          socket.close();
          debugger.waitForConnect();
          debugger.handshake();
          myMultiProcessDebugger.addDebugger(debugger);
          myMultiProcessDebugger.myDebugProcess.init();

          debugger.run();
        }
        catch (Exception e) {
        }
        finally {

        }
      }
    }
  }

  private void addDebugger(RemoteDebugger debugger) {
    myOtherDebuggers.add(debugger);
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
    myMainDebugger.execute(factory.createAddCommand(myMainDebugger));

    for (RemoteDebugger d : myOtherDebuggers) {
      d.execute(factory.createAddCommand(d));
    }
  }

  @Override
  public void removeExceptionBreakpoint(ExceptionBreakpointCommandFactory factory) {
    myMainDebugger.execute(factory.createRemoveCommand(myMainDebugger));

    for (RemoteDebugger d : myOtherDebuggers) {
      d.execute(factory.createRemoveCommand(d));
    }
  }

  public void remoteCloseListener(RemoteDebuggerCloseListener listener) {
    myMainDebugger.remoteCloseListener(listener);
  }
}
