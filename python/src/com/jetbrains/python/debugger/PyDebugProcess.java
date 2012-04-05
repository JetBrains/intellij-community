package com.jetbrains.python.debugger;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.xdebugger.*;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointHandler;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.intellij.xdebugger.stepping.XSmartStepIntoHandler;
import com.jetbrains.django.util.DjangoUtil;
import com.jetbrains.python.console.pydev.PydevCompletionVariant;
import com.jetbrains.python.debugger.django.DjangoExceptionBreakpointHandler;
import com.jetbrains.python.debugger.pydev.*;
import com.jetbrains.python.debugger.remote.vfs.PyRemotePositionConverter;
import com.jetbrains.python.remote.PyRemoteProcessHandlerBase;
import com.jetbrains.python.run.PythonProcessHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static javax.swing.SwingUtilities.invokeLater;

/**
 * @author yole
 */
// todo: bundle messages
// todo: pydevd supports module reloading - look for a way to use the feature
public class PyDebugProcess extends XDebugProcess implements IPyDebugProcess, ProcessListener {
  private final ProcessDebugger myDebugger;
  private final XBreakpointHandler[] myBreakpointHandlers;
  private final PyDebuggerEditorsProvider myEditorsProvider;
  private final ProcessHandler myProcessHandler;
  private final ExecutionConsole myExecutionConsole;
  private final Map<PySourcePosition, XLineBreakpoint> myRegisteredBreakpoints = new ConcurrentHashMap<PySourcePosition, XLineBreakpoint>();
  private final Map<String, XBreakpoint<? extends ExceptionBreakpointProperties>> myRegisteredExceptionBreakpoints =
    new ConcurrentHashMap<String, XBreakpoint<? extends ExceptionBreakpointProperties>>();

  private final List<PyThreadInfo> mySuspendedThreads = Lists.newArrayList();
  private final Map<String, XValueChildrenList> myStackFrameCache = Maps.newHashMap();
  private final Map<String, PyDebugValue> myNewVariableValue = Maps.newHashMap();

  private boolean myClosing = false;

  private PyPositionConverter myPositionConverter;
  private XSmartStepIntoHandler<?> mySmartStepIntoHandler;
  private boolean myWaitingForConnection = false;
  private PyStackFrame myStackFrameBeforeResume;

  public PyDebugProcess(final @NotNull XDebugSession session,
                        @NotNull final ServerSocket serverSocket,
                        @NotNull final ExecutionConsole executionConsole,
                        @Nullable final ProcessHandler processHandler, boolean multiProcess) {
    super(session);
    session.setPauseActionSupported(true);
    if (multiProcess) {
      myDebugger = createMultiprocessDebugger(serverSocket);
    }
    else {
      myDebugger = new RemoteDebugger(this, serverSocket, 10000);
    }
    myBreakpointHandlers = new XBreakpointHandler[]{new PyLineBreakpointHandler(this), new PyExceptionBreakpointHandler(this),
      new DjangoLineBreakpointHandler(this), new DjangoExceptionBreakpointHandler(this)};
    myEditorsProvider = new PyDebuggerEditorsProvider();
    mySmartStepIntoHandler = new PySmartStepIntoHandler(this);
    myProcessHandler = processHandler;
    myExecutionConsole = executionConsole;
    if (myProcessHandler != null) {
      myProcessHandler.addProcessListener(this);
    }
    if (processHandler instanceof PyRemoteProcessHandlerBase) {
      myPositionConverter = new PyRemotePositionConverter(this, ((PyRemoteProcessHandlerBase)processHandler).getMappingSettings());
    }
    else {
      myPositionConverter = new PyLocalPositionConverter();
    }
    myDebugger.addCloseListener(new RemoteDebuggerCloseListener() {
      @Override
      public void closed() {
        handleStop();
      }

      @Override
      public void communicationError() {
        handleCommunicationError();
      }

      @Override
      public void exitEvent() {
        handleCommunicationError();
      }
    });

    session.addSessionListener(new XDebugSessionAdapter() {
      @Override
      public void beforeSessionResume() {
        if (session.getCurrentStackFrame() instanceof PyStackFrame) {
          myStackFrameBeforeResume = (PyStackFrame)session.getCurrentStackFrame();
        }
        else {
          myStackFrameBeforeResume = null;
        }
      }
    });
  }

  private MultiProcessDebugger createMultiprocessDebugger(ServerSocket serverSocket) {
    MultiProcessDebugger debugger = new MultiProcessDebugger(this, serverSocket, 10000);
    debugger.setOtherDebuggerCloseListener(new MultiProcessDebugger.DebuggerProcessListener() {
      @Override
      public void threadsClosed(Set<String> threadIds) {
        for (PyThreadInfo t : mySuspendedThreads) {
          if (threadIds.contains(t.getId())) {
            if (getSession().isSuspended()) {
              getSession().resume();
              break;
            }
          }
        }
      }
    });
    return debugger;
  }

  protected void handleCommunicationError() {
    getSession().stop();
  }

  protected void handleStop() {
    getSession().stop();
  }

  public void setPositionConverter(PyPositionConverter positionConverter) {
    myPositionConverter = positionConverter;
  }


  @Override
  public PyPositionConverter getPositionConverter() {
    return myPositionConverter;
  }

  @Override
  public XBreakpointHandler<?>[] getBreakpointHandlers() {
    return myBreakpointHandlers;
  }

  @Override
  @NotNull
  public XDebuggerEditorsProvider getEditorsProvider() {
    return myEditorsProvider;
  }

  @Override
  @Nullable
  protected ProcessHandler doGetProcessHandler() {
    return myProcessHandler;
  }

  @Override
  @NotNull
  public ExecutionConsole createConsole() {
    return myExecutionConsole;
  }

  @Override
  public XSmartStepIntoHandler<?> getSmartStepIntoHandler() {
    return mySmartStepIntoHandler;
  }

  @Override
  public void sessionInitialized() {
    super.sessionInitialized();
    waitForConnection(getConnectionMessage(), getConnectionTitle());
  }

  protected void waitForConnection(final String connectionMessage, String connectionTitle) {
    ProgressManager.getInstance().run(new Task.Backgroundable(getSession().getProject(), connectionTitle, false) {
      public void run(@NotNull final ProgressIndicator indicator) {
        indicator.setText(connectionMessage);
        try {
          beforeConnect();
          myWaitingForConnection = true;
          myDebugger.waitForConnect();
          myWaitingForConnection = false;
          afterConnect();

          handshake();
          init();
          myDebugger.run();
        }
        catch (final Exception e) {
          myWaitingForConnection = false;
          myProcessHandler.destroyProcess();
          if (!myClosing) {
            invokeLater(new Runnable() {
              public void run() {
                Messages.showErrorDialog("Unable to establish connection with debugger:\n" + e.getMessage(), getConnectionTitle());
              }
            });
          }
        }
      }
    });
  }

  public void init() {
    getSession().rebuildViews();
    registerBreakpoints();
  }

  @Override
  public int handleDebugPort(int localPort) throws IOException {
    if (myProcessHandler instanceof PyRemoteProcessHandlerBase) {
      PyRemoteProcessHandlerBase remoteProcessHandler = (PyRemoteProcessHandlerBase)myProcessHandler;
      try {
        Pair<String, Integer> remoteSocket = remoteProcessHandler.obtainRemoteSocket();
        remoteProcessHandler.addRemoteForwarding(remoteSocket.getSecond(), localPort);
        return remoteSocket.getSecond();
      }
      catch (Exception e) {
        throw new IOException(e);
      }
    }
    else {
      return localPort;
    }
  }

  protected void afterConnect() {
  }

  protected void beforeConnect() {
  }

  protected String getConnectionMessage() {
    return "Connecting to debugger...";
  }

  protected String getConnectionTitle() {
    return "Connecting to debugger";
  }

  private void handshake() throws PyDebuggerException {
    String remoteVersion = myDebugger.handshake();
    String currentBuild = ApplicationInfo.getInstance().getBuild().asStringWithoutProductCode();
    if ("@@BUILD_NUMBER@@".equals(remoteVersion)) {
      remoteVersion = currentBuild;
    }
    else if (remoteVersion.startsWith("PY-")) {
      remoteVersion = remoteVersion.substring(3);
    }
    else {
      remoteVersion = null;
    }
    printToConsole("Connected to pydev debugger (build " + remoteVersion + ")\n", ConsoleViewContentType.SYSTEM_OUTPUT);

    if (remoteVersion != null) {
      if (!remoteVersion.equals(currentBuild)) {
        printToConsole("Warning: wrong debugger version. Use pycharm-debugger.egg from PyCharm installation folder.\n",
                       ConsoleViewContentType.ERROR_OUTPUT);
      }
    }
  }

  public void printToConsole(String text, ConsoleViewContentType contentType) {
    ((ConsoleView)myExecutionConsole).print(text, contentType);
  }

  private void registerBreakpoints() {
    registerLineBreakpoints();
    registerExceptionBreakpoints();
  }

  private void registerExceptionBreakpoints() {
    for (XBreakpoint<? extends ExceptionBreakpointProperties> bp : myRegisteredExceptionBreakpoints.values()) {
      addExceptionBreakpoint(bp);
    }
  }

  public void registerLineBreakpoints() {
    for (Map.Entry<PySourcePosition, XLineBreakpoint> entry : myRegisteredBreakpoints.entrySet()) {
      addBreakpoint(entry.getKey(), entry.getValue());
    }
  }

  @Override
  public void startStepOver() {
    passToCurrentThread(ResumeOrStepCommand.Mode.STEP_OVER);
  }

  @Override
  public void startStepInto() {
    passToCurrentThread(ResumeOrStepCommand.Mode.STEP_INTO);
  }

  @Override
  public void startStepOut() {
    passToCurrentThread(ResumeOrStepCommand.Mode.STEP_OUT);
  }

  public void startSmartStepInto(String functionName) {
    dropFrameCaches();
    if (isConnected()) {
      for (PyThreadInfo suspendedThread : mySuspendedThreads) {
        myDebugger.smartStepInto(suspendedThread.getId(), functionName);
      }
    }
  }

  @Override
  public void stop() {
    myDebugger.disconnect();
  }

  @Override
  public void resume() {
    passToAllThreads(ResumeOrStepCommand.Mode.RESUME);
  }

  @Override
  public void startPausing() {
    if (isConnected()) {
      myDebugger.suspendAllThreads();
    }
  }

  private void passToAllThreads(final ResumeOrStepCommand.Mode mode) {
    dropFrameCaches();
    if (isConnected()) {
      for (PyThreadInfo suspendedThread : mySuspendedThreads) {
        myDebugger.resumeOrStep(suspendedThread.getId(), mode);
      }
    }
  }

  private void passToCurrentThread(final ResumeOrStepCommand.Mode mode) {
    dropFrameCaches();
    if (isConnected()) {
      String threadId = threadIdBeforeResumeOrStep();

      for (PyThreadInfo suspendedThread : mySuspendedThreads) {
        if (threadId == null || threadId.equals(suspendedThread.getId())) {
          myDebugger.resumeOrStep(suspendedThread.getId(), mode);
          break;
        }
      }
    }
  }

  @Nullable
  private String threadIdBeforeResumeOrStep() {
    String threadId = null;
    if (myStackFrameBeforeResume != null) {
      threadId = myStackFrameBeforeResume.getThreadId();
    }

    return threadId;
  }

  protected boolean isConnected() {
    return myDebugger.isConnected();
  }

  protected void disconnect() {
    myDebugger.disconnect();
    cleanUp();
  }

  private void cleanUp() {
    mySuspendedThreads.clear();
  }

  @Override
  public void runToPosition(@NotNull final XSourcePosition position) {
    dropFrameCaches();
    if (isConnected() && !mySuspendedThreads.isEmpty()) {
      final PySourcePosition pyPosition = myPositionConverter.convert(position);
      String type = PyLineBreakpointType.ID;
      final Document document = FileDocumentManager.getInstance().getDocument(position.getFile());
      if (document != null) {
        if (DjangoUtil.isDjangoTemplateDocument(document, getSession().getProject())) {
          type = DjangoTemplateLineBreakpointType.ID;
        }
      }
      myDebugger.setTempBreakpoint(type, pyPosition.getFile(), pyPosition.getLine());

      passToCurrentThread(ResumeOrStepCommand.Mode.RESUME);
    }
  }

  public PyDebugValue evaluate(final String expression, final boolean execute, boolean doTrunc) throws PyDebuggerException {
    dropFrameCaches();
    final PyStackFrame frame = currentFrame();
    return evaluate(expression, execute, frame, doTrunc);
  }

  private PyDebugValue evaluate(String expression, boolean execute, PyStackFrame frame, boolean trimResult) throws PyDebuggerException {
    return myDebugger.evaluate(frame.getThreadId(), frame.getFrameId(), expression, execute, trimResult);
  }

  public String consoleExec(String command) throws PyDebuggerException {
    dropFrameCaches();
    final PyStackFrame frame = currentFrame();
    return myDebugger.consoleExec(frame.getThreadId(), frame.getFrameId(), command);
  }

  @Nullable
  public XValueChildrenList loadFrame() throws PyDebuggerException {
    final PyStackFrame frame = currentFrame();
    //do not reload frame every time it is needed, because due to bug in pdb, reloading frame clears all variable changes
    if (!myStackFrameCache.containsKey(frame.getThreadFrameId())) {
      XValueChildrenList values = myDebugger.loadFrame(frame.getThreadId(), frame.getFrameId());
      myStackFrameCache.put(frame.getThreadFrameId(), values);
    }
    return applyNewValue(myStackFrameCache.get(frame.getThreadFrameId()), frame.getThreadFrameId());
  }

  private XValueChildrenList applyNewValue(XValueChildrenList pyDebugValues, String threadFrameId) {
    if (myNewVariableValue.containsKey(threadFrameId)) {
      PyDebugValue newValue = myNewVariableValue.get(threadFrameId);
      XValueChildrenList res = new XValueChildrenList();
      for (int i = 0; i < pyDebugValues.size(); i++) {
        final String name = pyDebugValues.getName(i);
        if (name.equals(newValue.getName())) {
          res.add(name, newValue);
        }
        else {
          res.add(name, pyDebugValues.getValue(i));
        }
      }
      return res;
    }
    else {
      return pyDebugValues;
    }
  }

  @Override
  public XValueChildrenList loadVariable(final PyDebugValue var) throws PyDebuggerException {
    final PyStackFrame frame = currentFrame();
    return myDebugger.loadVariable(frame.getThreadId(), frame.getFrameId(), var);
  }

  @Override
  public void changeVariable(final PyDebugValue var, final String value) throws PyDebuggerException {
    final PyStackFrame frame = currentFrame();
    PyDebugValue newValue = myDebugger.changeVariable(frame.getThreadId(), frame.getFrameId(), var, value);
    myNewVariableValue.put(frame.getThreadFrameId(), newValue);
  }

  @Nullable
  public String loadSource(String path) {
    return myDebugger.loadSource(path);
  }

  @Override
  public boolean isVariable(String name) {
    final Project project = getSession().getProject();
    return PyDebugSupportUtils.isVariable(project, name);
  }

  private PyStackFrame currentFrame() throws PyDebuggerException {
    if (!isConnected()) {
      throw new PyDebuggerException("Disconnected");
    }

    final PyStackFrame frame = (PyStackFrame)getSession().getCurrentStackFrame();
    if (frame == null) {
      throw new PyDebuggerException("Process is running");
    }

    return frame;
  }

  public void addBreakpoint(final PySourcePosition position, final XLineBreakpoint breakpoint) {
    myRegisteredBreakpoints.put(position, breakpoint);
    if (isConnected()) {
      myDebugger.setBreakpoint(breakpoint.getType().getId(), position.getFile(), position.getLine(),
                               breakpoint.getCondition(),
                               breakpoint.getLogExpression());
    }
  }

  public void removeBreakpoint(final PySourcePosition position) {
    XLineBreakpoint breakpoint = myRegisteredBreakpoints.get(position);
    if (breakpoint != null) {
      myRegisteredBreakpoints.remove(position);
      if (isConnected()) {
        myDebugger.removeBreakpoint(breakpoint.getType().getId(), position.getFile(), position.getLine());
      }
    }
  }

  public void addExceptionBreakpoint(XBreakpoint<? extends ExceptionBreakpointProperties> breakpoint) {
    myRegisteredExceptionBreakpoints.put(breakpoint.getProperties().getException(), breakpoint);
    if (isConnected()) {
      myDebugger.addExceptionBreakpoint(breakpoint.getProperties());
    }
  }

  public void removeExceptionBreakpoint(XBreakpoint<? extends ExceptionBreakpointProperties> breakpoint) {
    myRegisteredExceptionBreakpoints.remove(breakpoint.getProperties().getException());
    if (isConnected()) {
      myDebugger.removeExceptionBreakpoint(breakpoint.getProperties());
    }
  }

  public Collection<PyThreadInfo> getThreads() {
    return myDebugger.getThreads();
  }

  @Override
  public void threadSuspended(final PyThreadInfo threadInfo) {
    if (!mySuspendedThreads.contains(threadInfo)) {
      mySuspendedThreads.add(threadInfo);

      final List<PyStackFrameInfo> frames = threadInfo.getFrames();
      if (frames != null) {
        final PySuspendContext suspendContext = new PySuspendContext(this, threadInfo);

        XBreakpoint<?> breakpoint = null;
        if (threadInfo.isStopOnBreakpoint()) {
          final PySourcePosition position = frames.get(0).getPosition();
          breakpoint = myRegisteredBreakpoints.get(position);
          if (breakpoint == null) {
            myDebugger.removeTempBreakpoint(position.getFile(), position.getLine());
          }
        }
        else if (threadInfo.isExceptionBreak()) {
          String exceptionName = threadInfo.getMessage();
          threadInfo.setMessage(null);
          if (exceptionName != null) {
            breakpoint = myRegisteredExceptionBreakpoints.get(exceptionName);
          }
        }

        if (breakpoint != null) {
          if (!getSession().breakpointReached(breakpoint, threadInfo.getMessage(), suspendContext)) {
            resume();
          }
        }
        else {
          getSession().positionReached(suspendContext);
        }
      }
    }
  }

  @Override
  public void threadResumed(final PyThreadInfo threadInfo) {
    mySuspendedThreads.remove(threadInfo);
  }

  private void dropFrameCaches() {
    myStackFrameCache.clear();
    myNewVariableValue.clear();
  }

  @NotNull
  public List<PydevCompletionVariant> getCompletions(String prefix) throws Exception {
    if (isConnected()) {
      dropFrameCaches();
      final PyStackFrame frame = currentFrame();
      return myDebugger.getCompletions(frame.getThreadId(), frame.getFrameId(), prefix);
    }
    return Lists.newArrayList();
  }

  @Override
  public void startNotified(ProcessEvent event) {
  }

  @Override
  public void processTerminated(ProcessEvent event) {
    myDebugger.close();
  }

  @Override
  public void processWillTerminate(ProcessEvent event, boolean willBeDestroyed) {
    myClosing = true;
    setKillingStrategy();
  }

  private void setKillingStrategy() {
    if (getSession().isSuspended() && myProcessHandler instanceof PythonProcessHandler) {
      ((PythonProcessHandler)myProcessHandler)
        .setShouldTryToKillSoftly(false);    //while process is suspended it can't terminate softly, so its better to kill all the tree hard
    }
  }

  @Override
  public void onTextAvailable(ProcessEvent event, Key outputType) {
  }

  public PyStackFrame createStackFrame(PyStackFrameInfo frameInfo) {
    return new PyStackFrame(this, frameInfo);
  }

  @Override
  public String getCurrentStateMessage() {
    if (getSession().isStopped()) {
      return XDebuggerBundle.message("debugger.state.message.disconnected");
    }
    else if (isConnected()) {
      return XDebuggerBundle.message("debugger.state.message.connected");
    }
    else {
      return "Waiting for connection...";
    }
  }

  public void addProcessListener(ProcessListener listener) {
    ProcessHandler handler = doGetProcessHandler();
    if (handler != null) {
      handler.addProcessListener(listener);
    }
  }

  public boolean isWaitingForConnection() {
    return myWaitingForConnection;
  }

  public void setWaitingForConnection(boolean waitingForConnection) {
    myWaitingForConnection = waitingForConnection;
  }
}
