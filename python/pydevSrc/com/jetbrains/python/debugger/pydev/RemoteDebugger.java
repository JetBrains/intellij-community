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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.BaseOutputReader;
import com.intellij.xdebugger.breakpoints.SuspendPolicy;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.jetbrains.python.console.pydev.PydevCompletionVariant;
import com.jetbrains.python.debugger.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;


public class RemoteDebugger implements ProcessDebugger {
  private static final int RESPONSE_TIMEOUT = 60000;

  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.pydev.remote.RemoteDebugger");

  private static final String LOCAL_VERSION = "0.1";
  public static final String TEMP_VAR_PREFIX = "__py_debug_temp_var_";

  private static final SecureRandom ourRandom = new SecureRandom();

  private final IPyDebugProcess myDebugProcess;

  @NotNull
  private final ServerSocket myServerSocket;

  private final int myConnectionTimeout;
  private final Object mySocketObject = new Object(); // for synchronization on socket
  private Socket mySocket;
  private volatile boolean myConnected = false;
  private int mySequence = -1;
  private final Object mySequenceObject = new Object(); // for synchronization on mySequence
  private final Map<String, PyThreadInfo> myThreads = new ConcurrentHashMap<>();
  private final Map<Integer, ProtocolFrame> myResponseQueue = new HashMap<>();
  private final TempVarsHolder myTempVars = new TempVarsHolder();

  private Map<Pair<String, Integer>, String> myTempBreakpoints = Maps.newHashMap();


  private final List<RemoteDebuggerCloseListener> myCloseListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private DebuggerReader myDebuggerReader;

  public RemoteDebugger(final IPyDebugProcess debugProcess, @NotNull final ServerSocket serverSocket, final int timeout) {
    myDebugProcess = debugProcess;
    myServerSocket = serverSocket;
    myConnectionTimeout = timeout;
  }

  public IPyDebugProcess getDebugProcess() {
    return myDebugProcess;
  }

  @Override
  public boolean isConnected() {
    return myConnected;
  }


  @Override
  public void waitForConnect() throws Exception {
    try {
      //noinspection SocketOpenedButNotSafelyClosed
      myServerSocket.setSoTimeout(myConnectionTimeout);
      synchronized (mySocketObject) {
        mySocket = myServerSocket.accept();
        myConnected = true;
      }
    }
    finally {
      //it is closed in close() method on process termination
    }

    if (myConnected) {
      try {
        myDebuggerReader = createReader();
      }
      catch (Exception e) {
        synchronized (mySocketObject) {
          mySocket.close();
        }
        throw e;
      }
    }
  }

  @Override
  public String handshake() throws PyDebuggerException {
    final VersionCommand command = new VersionCommand(this, LOCAL_VERSION, SystemInfo.isUnix ? "UNIX" : "WIN");
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
    final EvaluateCommand command = new EvaluateCommand(this, threadId, frameId, expression, execute, trimResult);
    command.execute();
    return command.getValue();
  }

  @Override
  public void consoleExec(String threadId, String frameId, String expression, PyDebugCallback<String> callback) {
    final ConsoleExecCommand command = new ConsoleExecCommand(this, threadId, frameId, expression);
    command.execute(callback);
  }

  @Override
  public XValueChildrenList loadFrame(final String threadId, final String frameId) throws PyDebuggerException {
    final GetFrameCommand command = new GetFrameCommand(this, threadId, frameId);
    command.execute();
    return command.getVariables();
  }

  // todo: don't generate temp variables for qualified expressions - just split 'em
  @Override
  public XValueChildrenList loadVariable(final String threadId, final String frameId, final PyDebugValue var) throws PyDebuggerException {
    setTempVariable(threadId, frameId, var);
    final GetVariableCommand command = new GetVariableCommand(this, threadId, frameId, var);
    command.execute();
    return command.getVariables();
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
    final GetArrayCommand command = new GetArrayCommand(this, threadId, frameId, var, rowOffset, colOffset, rows, cols, format);
    command.execute();
    return command.getArray();
  }


  @Override
  public void loadReferrers(final String threadId,
                            final String frameId,
                            final PyReferringObjectsValue var,
                            final PyDebugCallback<XValueChildrenList> callback) {
    RunCustomOperationCommand cmd = new GetReferrersCommand(this, threadId, frameId, var);

    cmd.execute(new PyDebugCallback<List<PyDebugValue>>() {
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
    final ChangeVariableCommand command = new ChangeVariableCommand(this, threadId, frameId, varName, value);
    command.execute();
    return command.getNewValue();
  }

  @Override
  @Nullable
  public String loadSource(String path) {
    LoadSourceCommand command = new LoadSourceCommand(this, path);
    try {
      command.execute();
      return command.getContent();
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
    if (!myDebugProcess.canSaveToTemp(topVar.getName())) {
      return;
    }
    if (myTempVars.contains(threadId, frameId, topVar.getTempName())) {
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

  private void clearTempVariables(final String threadId) {
    final Map<String, Set<String>> threadVars = myTempVars.get(threadId);
    if (threadVars == null || threadVars.size() == 0) return;

    for (Map.Entry<String, Set<String>> entry : threadVars.entrySet()) {
      final Set<String> frameVars = entry.getValue();
      if (frameVars == null || frameVars.size() == 0) continue;

      final String expression = "del " + StringUtil.join(frameVars, ",");
      try {
        evaluate(threadId, entry.getKey(), expression, true);
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
  ProtocolFrame waitForResponse(final int sequence) {
    ProtocolFrame response;
    long until = System.currentTimeMillis() + RESPONSE_TIMEOUT;

    synchronized (myResponseQueue) {
      do {
        try {
          myResponseQueue.wait(1000);
        }
        catch (InterruptedException ignore) {
        }
        response = myResponseQueue.get(sequence);
      }
      while (response == null && isConnected() && System.currentTimeMillis() < until);
      myResponseQueue.remove(sequence);
    }

    return response;
  }

  @Override
  public void execute(@NotNull final AbstractCommand command) {
    if (command instanceof ResumeOrStepCommand) {
      final String threadId = ((ResumeOrStepCommand)command).getThreadId();
      clearTempVariables(threadId);
    }

    try {
      command.execute();
    }
    catch (PyDebuggerException e) {
      LOG.error(e);
    }
  }

  boolean sendFrame(final ProtocolFrame frame) {
    logFrame(frame, true);

    try {
      final byte[] packed = frame.pack();
      synchronized (mySocketObject) {
        final OutputStream os = mySocket.getOutputStream();
        os.write(packed);
        os.flush();
        return true;
      }
    }
    catch (SocketException se) {
      disconnect();
      fireCommunicationError();
    }
    catch (IOException e) {
      LOG.error(e);
    }
    return false;
  }

  private static void logFrame(ProtocolFrame frame, boolean out) {
    if (LOG.isDebugEnabled()) {
      LOG.debug(String.format("%1$tH:%1$tM:%1$tS.%1$tL %2$s %3$s\n", new Date(), (out ? "<<<" : ">>>"), frame));
    }
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
    if (!myServerSocket.isClosed()) {
      try {
        myServerSocket.close();
      }
      catch (IOException e) {
        LOG.warn("Error closing socket", e);
      }
    }
    if (myDebuggerReader != null) {
      myDebuggerReader.stop();
    }
    fireCloseEvent();
  }

  @Override
  public void disconnect() {
    synchronized (mySocketObject) {
      myConnected = false;

      if (mySocket != null && !mySocket.isClosed()) {
        try {
          mySocket.close();
        }
        catch (IOException ignore) {
        }
      }
    }

    cleanUp();
  }

  @Override
  public void run() throws PyDebuggerException {
    new RunCommand(this).execute();
  }

  @Override
  public void smartStepInto(String threadId, String functionName) {
    final SmartStepIntoCommand command = new SmartStepIntoCommand(this, threadId, functionName);
    execute(command);
  }

  @Override
  public void resumeOrStep(String threadId, ResumeOrStepCommand.Mode mode) {
    final ResumeOrStepCommand command = new ResumeOrStepCommand(this, threadId, mode);
    execute(command);
  }

  @Override
  public void setTempBreakpoint(@NotNull String type, @NotNull String file, int line) {
    final SetBreakpointCommand command =
      new SetBreakpointCommand(this, type, file, line);
    execute(command);  // set temp. breakpoint
    myTempBreakpoints.put(Pair.create(file, line), type);
  }

  @Override
  public void removeTempBreakpoint(@NotNull String file, int line) {
    String type = myTempBreakpoints.get(Pair.create(file, line));
    if (type != null) {
      final RemoveBreakpointCommand command = new RemoveBreakpointCommand(this, type, file, line);
      execute(command);  // remove temp. breakpoint
    }
    else {
      LOG.error("Temp breakpoint not found for " + file + ":" + line);
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
  public void setShowReturnValues(boolean isShowReturnValues) {
    final ShowReturnValuesCommand command = new ShowReturnValuesCommand(this, isShowReturnValues);
    execute(command);
  }

  private DebuggerReader createReader() throws IOException {
    synchronized (mySocketObject) {
      //noinspection IOResourceOpenedButNotSafelyClosed
      return new DebuggerReader(mySocket.getInputStream());
    }
  }

  private class DebuggerReader extends BaseOutputReader {
    private StringBuilder myTextBuilder = new StringBuilder();

    private DebuggerReader(final InputStream stream) throws IOException {
      super(stream, CharsetToolkit.UTF8_CHARSET); //TODO: correct encoding?
      start(getClass().getName());
    }

    protected void doRun() {
      try {
        while (true) {
          boolean read = readAvailableBlocking();

          if (!read) {
            break;
          }
          else {
            if (isStopped) {
              break;
            }

            TimeoutUtil.sleep(mySleepingPolicy.getTimeToSleep(true));
          }
        }
      }
      catch (Exception e) {
        fireCommunicationError();
      }
      finally {
        close();
        fireExitEvent();
      }
    }

    private void processResponse(final String line) {
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
          myDebugProcess.consoleInputRequested();
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
      switch (frame.getCommand()) {
        case AbstractCommand.CREATE_THREAD: {
          final PyThreadInfo thread = parseThreadEvent(frame);
          if (!thread.isPydevThread()) {  // ignore pydevd threads
            myThreads.put(thread.getId(), thread);
            if (myDebugProcess.getSession().isSuspended() && myDebugProcess.isSuspendedOnAllThreadsPolicy()) {
              // Sometimes the notification about new threads may come slow from the Python side. We should check if
              // the current session is suspended in the "Suspend all threads" mode and suspend new thread, which hasn't been suspended
              suspendThread(thread.getId());
            }
          }
          break;
        }
        case AbstractCommand.SUSPEND_THREAD: {
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
          if (event.getStopReason() == AbstractCommand.SUSPEND_THREAD) {
            // That means that the thread was stopped manually from the Java side either while suspending all threads
            // or after the "Pause" command. In both cases we shouldn't change debugger focus if session is already suspended.
            updateSourcePosition = !myDebugProcess.getSession().isSuspended();
          }
          myDebugProcess.threadSuspended(thread, updateSourcePosition);
          break;
        }
        case AbstractCommand.RESUME_THREAD: {
          final String id = ProtocolParser.getThreadId(frame.getPayload());
          final PyThreadInfo thread = myThreads.get(id);
          if (thread != null) {
            thread.updateState(PyThreadInfo.State.RUNNING, null);
            myDebugProcess.threadResumed(thread);
          }
          break;
        }
        case AbstractCommand.KILL_THREAD: {
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
          break;
        }
        case AbstractCommand.SHOW_CONSOLE: {
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
          break;
        }
      }
    }

    private PyThreadInfo parseThreadEvent(ProtocolFrame frame) throws PyDebuggerException {
      return ProtocolParser.parseThread(frame.getPayload(), myDebugProcess.getPositionConverter());
    }

    @NotNull
    @Override
    protected Future<?> executeOnPooledThread(@NotNull Runnable runnable) {
      return ApplicationManager.getApplication().executeOnPooledThread(runnable);
    }

    @Override
    protected void close() {
      try {
        super.close();
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }

    @Override
    public void stop() {
      super.stop();
      close();
    }

    @Override
    protected void onTextAvailable(@NotNull String text) {
      myTextBuilder.append(text);
      if (text.contains("\n")) {
        String[] lines = myTextBuilder.toString().split("\n");
        myTextBuilder = new StringBuilder();

        if (!text.endsWith("\n")) {
          myTextBuilder.append(lines[lines.length - 1]);
          lines = Arrays.copyOfRange(lines, 0, lines.length - 1);
        }

        for (String line : lines) {
          processResponse(line + "\n");
        }
      }
    }
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


  private static class TempVarsHolder {
    private final Map<String, Map<String, Set<String>>> myData = new HashMap<>();

    public boolean contains(final String threadId, final String frameId, final String name) {
      final Map<String, Set<String>> threadVars = myData.get(threadId);
      if (threadVars == null) return false;

      final Set<String> frameVars = threadVars.get(frameId);
      if (frameVars == null) return false;

      return frameVars.contains(name);
    }

    private void put(final String threadId, final String frameId, final String name) {
      Map<String, Set<String>> threadVars = myData.get(threadId);
      if (threadVars == null) myData.put(threadId, (threadVars = new HashMap<>()));

      Set<String> frameVars = threadVars.get(frameId);
      if (frameVars == null) threadVars.put(frameId, (frameVars = new HashSet<>()));

      frameVars.add(name);
    }

    private Map<String, Set<String>> get(final String threadId) {
      return myData.get(threadId);
    }

    private void clear() {
      myData.clear();
    }

    private void clear(final String threadId) {
      final Map<String, Set<String>> threadVars = myData.get(threadId);
      if (threadVars != null) {
        threadVars.clear();
      }
    }
  }

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

  private void fireCloseEvent() {
    for (RemoteDebuggerCloseListener listener : myCloseListeners) {
      listener.closed();
    }
  }

  private void fireCommunicationError() {
    for (RemoteDebuggerCloseListener listener : myCloseListeners) {
      listener.communicationError();
    }
  }

  private void fireExitEvent() {
    for (RemoteDebuggerCloseListener listener : myCloseListeners) {
      listener.detached();
    }
  }
}
