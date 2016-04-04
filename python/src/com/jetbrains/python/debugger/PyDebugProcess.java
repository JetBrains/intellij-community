/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.python.debugger;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.remote.RemoteProcessControl;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.*;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointHandler;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.intellij.xdebugger.impl.XSourcePositionImpl;
import com.intellij.xdebugger.stepping.XSmartStepIntoHandler;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.console.PythonDebugLanguageConsoleView;
import com.jetbrains.python.console.pydev.PydevCompletionVariant;
import com.jetbrains.python.debugger.pydev.*;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static javax.swing.SwingUtilities.invokeLater;

/**
 * @author yole
 */
// todo: bundle messages
// todo: pydevd supports module reloading - look for a way to use the feature
public class PyDebugProcess extends XDebugProcess implements IPyDebugProcess, ProcessListener {

  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.debugger.PyDebugProcess");
  private static final int CONNECTION_TIMEOUT = 60000;

  private final ProcessDebugger myDebugger;
  private final XBreakpointHandler[] myBreakpointHandlers;
  private final PyDebuggerEditorsProvider myEditorsProvider;
  private final ProcessHandler myProcessHandler;
  private final ExecutionConsole myExecutionConsole;
  private final Map<PySourcePosition, XLineBreakpoint> myRegisteredBreakpoints = new ConcurrentHashMap<PySourcePosition, XLineBreakpoint>();
  private final Map<String, XBreakpoint<? extends ExceptionBreakpointProperties>> myRegisteredExceptionBreakpoints =
    new ConcurrentHashMap<String, XBreakpoint<? extends ExceptionBreakpointProperties>>();

  private final List<PyThreadInfo> mySuspendedThreads = Collections.synchronizedList(Lists.<PyThreadInfo>newArrayList());
  private final Map<String, XValueChildrenList> myStackFrameCache = Maps.newHashMap();
  private final Map<String, PyDebugValue> myNewVariableValue = Maps.newHashMap();
  private boolean myDownloadSources = false;

  private boolean myClosing = false;

  private PyPositionConverter myPositionConverter;
  private final XSmartStepIntoHandler<?> mySmartStepIntoHandler;
  private boolean myWaitingForConnection = false;
  private PyStackFrame myStackFrameBeforeResume;
  private PyStackFrame myConsoleContextFrame = null;
  private PyReferrersLoader myReferrersProvider;

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
      myDebugger = new RemoteDebugger(this, serverSocket, getConnectTimeout());
    }

    List<XBreakpointHandler> breakpointHandlers = new ArrayList<XBreakpointHandler>();
    breakpointHandlers.add(new PyLineBreakpointHandler(this));
    breakpointHandlers.add(new PyExceptionBreakpointHandler(this));
    for (PyBreakpointHandlerFactory factory : Extensions.getExtensions(PyBreakpointHandlerFactory.EP_NAME)) {
      breakpointHandlers.add(factory.createBreakpointHandler(this));
    }
    myBreakpointHandlers = breakpointHandlers.toArray(new XBreakpointHandler[breakpointHandlers.size()]);

    myEditorsProvider = new PyDebuggerEditorsProvider();
    mySmartStepIntoHandler = new PySmartStepIntoHandler(this);
    myProcessHandler = processHandler;
    myExecutionConsole = executionConsole;
    if (myProcessHandler != null) {
      myProcessHandler.addProcessListener(this);
    }
    if (processHandler instanceof PositionConverterProvider) {
      myPositionConverter = ((PositionConverterProvider)processHandler).createPositionConverter(this);
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
        detachDebuggedProcess();
      }

      @Override
      public void detached() {
        detachDebuggedProcess();
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
    debugger.addOtherDebuggerCloseListener(new MultiProcessDebugger.DebuggerProcessListener() {
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

  protected void detachDebuggedProcess() {
    handleStop(); //in case of normal debug we stop the session
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

  @NotNull
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
    waitForConnection(getConnectionMessage(), getConnectionTitle());
  }

  protected void waitForConnection(final String connectionMessage, String connectionTitle) {
    ProgressManager.getInstance().run(new Task.Backgroundable(getSession().getProject(), connectionTitle, false) {
      @Override
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
          if (myProcessHandler != null) {
            myProcessHandler.destroyProcess();
          }
          if (!myClosing) {
            invokeLater(new Runnable() {
              @Override
              public void run() {
                Messages.showErrorDialog("Unable to establish connection with debugger:\n" + e.getMessage(), getConnectionTitle());
              }
            });
          }
        }
      }
    });
  }

  @Override
  public void init() {
    getSession().rebuildViews();
    registerBreakpoints();
  }

  @Override
  public int handleDebugPort(int localPort) throws IOException {
    if (myProcessHandler instanceof RemoteProcessControl) {
      return getRemoteTunneledPort(localPort, (RemoteProcessControl)myProcessHandler);
    }
    else {
      return localPort;
    }
  }

  protected static int getRemoteTunneledPort(int localPort, @NotNull RemoteProcessControl handler) throws IOException {
    try {
      return handler.getRemoteSocket(localPort).getSecond();
    }
    catch (Exception e) {
      throw new IOException(e);
    }
  }

  @Override
  public void recordSignature(PySignature signature) {
    PySignatureCacheManager.getInstance(getSession().getProject()).recordSignature(myPositionConverter.convertSignature(signature));
  }

  @Override
  public void recordLogEvent(PyConcurrencyEvent event) {
    PyConcurrencyService.getInstance(getSession().getProject()).recordEvent(getSession(), event, event.isAsyncio());
  }

  @Override
  public void showConsole(PyThreadInfo thread) {
    myConsoleContextFrame = new PyExecutionStack(this, thread).getTopFrame();
    if (myExecutionConsole instanceof PythonDebugLanguageConsoleView) {
      UIUtil.invokeLaterIfNeeded(new Runnable() {
        @Override
        public void run() {
          ((PythonDebugLanguageConsoleView)myExecutionConsole).enableConsole(false);
        }
      });
    }
  }

  protected void afterConnect() {
  }

  protected void beforeConnect() {
  }

  protected String getConnectionMessage() {
    return "Waiting for connection...";
  }

  protected String getConnectionTitle() {
    return "Connecting To Debugger";
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
      if (!(remoteVersion.equals(currentBuild) || remoteVersion.startsWith(currentBuild))) {
        LOG.warn(String.format("Wrong debugger version. Remote version: %s Current build: %s", remoteVersion, currentBuild));
        printToConsole("Warning: wrong debugger version. Use pycharm-debugger.egg from PyCharm installation folder.\n",
                       ConsoleViewContentType.ERROR_OUTPUT);
      }
    }
  }

  @Override
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

  public void startStepIntoMyCode() {
    if (!checkCanPerformCommands()) return;
    getSession().sessionResumed();
    passToCurrentThread(ResumeOrStepCommand.Mode.STEP_INTO_MY_CODE);
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
    myDebugger.close();
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
      for (PyThreadInfo suspendedThread : Lists.newArrayList(mySuspendedThreads)) {
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

  public boolean isDownloadSources() {
    return myDownloadSources;
  }

  public void setDownloadSources(boolean downloadSources) {
    myDownloadSources = downloadSources;
  }

  protected void cleanUp() {
    mySuspendedThreads.clear();
    myDownloadSources = false;
  }

  @Override
  public void runToPosition(@NotNull final XSourcePosition position) {
    dropFrameCaches();
    if (isConnected() && !mySuspendedThreads.isEmpty()) {
      final PySourcePosition pyPosition = myPositionConverter.convertToPython(position);
      String type = PyLineBreakpointType.ID;
      AccessToken lock = ApplicationManager.getApplication().acquireReadActionLock();
      try {
        final Document document = FileDocumentManager.getInstance().getDocument(position.getFile());
        if (document != null) {
          for (XBreakpointType breakpointType : Extensions.getExtensions(XBreakpointType.EXTENSION_POINT_NAME)) {
            if (breakpointType instanceof PyBreakpointType &&
                ((PyBreakpointType)breakpointType).canPutInDocument(getSession().getProject(), document)) {
              type = breakpointType.getId();
              break;
            }
          }
        }
      }
      finally {
        lock.finish();
      }
      myDebugger.setTempBreakpoint(type, pyPosition.getFile(), pyPosition.getLine());

      passToCurrentThread(ResumeOrStepCommand.Mode.RESUME);
    }
  }

  @Override
  public PyDebugValue evaluate(final String expression, final boolean execute, boolean doTrunc) throws PyDebuggerException {
    dropFrameCaches();
    final PyStackFrame frame = currentFrame();
    return evaluate(expression, execute, frame, doTrunc);
  }

  private PyDebugValue evaluate(String expression, boolean execute, PyStackFrame frame, boolean trimResult) throws PyDebuggerException {
    return myDebugger.evaluate(frame.getThreadId(), frame.getFrameId(), expression, execute, trimResult);
  }

  public void consoleExec(String command, PyDebugCallback<String> callback) {
    dropFrameCaches();
    try {
      final PyStackFrame frame = currentFrame();
      myDebugger.consoleExec(frame.getThreadId(), frame.getFrameId(), command, callback);
    }
    catch (PyDebuggerException e) {
      callback.error(e);
    }
  }

  @Override
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
  public void loadReferrers(PyReferringObjectsValue var, PyDebugCallback<XValueChildrenList> callback) {
    try {
      final PyStackFrame frame = currentFrame();
      myDebugger.loadReferrers(frame.getThreadId(), frame.getFrameId(), var, callback);
    }
    catch (PyDebuggerException e) {
      callback.error(e);
    }
  }

  @Override
  public void changeVariable(final PyDebugValue var, final String value) throws PyDebuggerException {
    final PyStackFrame frame = currentFrame();
    PyDebugValue newValue = myDebugger.changeVariable(frame.getThreadId(), frame.getFrameId(), var, value);
    myNewVariableValue.put(frame.getThreadFrameId(), newValue);
  }

  @Nullable
  @Override
  public PyReferrersLoader getReferrersLoader() {
    if (myReferrersProvider == null) {
      myReferrersProvider = new PyReferrersLoader(this);
    }
    return myReferrersProvider;
  }

  @Override
  public ArrayChunk getArrayItems(PyDebugValue var, int rowOffset, int colOffset, int rows, int cols, String format)
    throws PyDebuggerException {
    final PyStackFrame frame = currentFrame();
    return myDebugger.loadArrayItems(frame.getThreadId(), frame.getFrameId(), var, rowOffset, colOffset, rows, cols, format);
  }

  @Nullable
  public String loadSource(String path) {
    return myDebugger.loadSource(path);
  }

  @Override
  public boolean canSaveToTemp(String name) {
    final Project project = getSession().getProject();
    return PyDebugSupportUtils.canSaveToTemp(project, name);
  }

  @NotNull
  private PyStackFrame currentFrame() throws PyDebuggerException {
    if (!isConnected()) {
      throw new PyDebuggerException("Disconnected");
    }

    final PyStackFrame frame = (PyStackFrame)getSession().getCurrentStackFrame();

    if (frame == null && myConsoleContextFrame != null) {
      return myConsoleContextFrame;
    }

    if (frame == null) {
      throw new PyDebuggerException("Process is running");
    }

    return frame;
  }

  @Nullable
  private String getFunctionName(final XLineBreakpoint breakpoint) {
    if (breakpoint.getSourcePosition() == null) {
      return null;
    }
    final VirtualFile file = breakpoint.getSourcePosition().getFile();
    AccessToken lock = ApplicationManager.getApplication().acquireReadActionLock();
    try {
      final Document document = FileDocumentManager.getInstance().getDocument(file);
      final Project project = getSession().getProject();
      if (document != null) {
        if (file.getFileType() == PythonFileType.INSTANCE) {
          PsiElement psiElement = XDebuggerUtil.getInstance().findContextElement(file, breakpoint.getSourcePosition().getOffset(),
                                                                                 project, false);
          PyFunction function = PsiTreeUtil.getParentOfType(psiElement, PyFunction.class);
          if (function != null) {
            return function.getName();
          }
        }
      }
      return null;
    }
    finally {
      lock.finish();
    }
  }

  public void addBreakpoint(final PySourcePosition position, final XLineBreakpoint breakpoint) {
    myRegisteredBreakpoints.put(position, breakpoint);
    if (isConnected()) {
      final String conditionExpression = breakpoint.getConditionExpression() == null
                                         ? null
                                         : breakpoint.getConditionExpression().getExpression();
      final String logExpression = breakpoint.getLogExpressionObject() == null
                                   ? null
                                   : breakpoint.getLogExpressionObject().getExpression();
      myDebugger.setBreakpointWithFuncName(breakpoint.getType().getId(), position.getFile(), position.getLine(),
                                           conditionExpression,
                                           logExpression,
                                           getFunctionName(breakpoint));
    }
  }

  public void addTemporaryBreakpoint(String typeId, String file, int line) {
    if (isConnected()) {
      myDebugger.setTempBreakpoint(typeId, file, line);
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
        final PySuspendContext suspendContext = createSuspendContext(threadInfo);

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

  @NotNull
  protected PySuspendContext createSuspendContext(PyThreadInfo threadInfo) {
    return new PySuspendContext(this, threadInfo);
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
  }

  @Override
  public void onTextAvailable(ProcessEvent event, Key outputType) {
  }

  public PyStackFrame createStackFrame(PyStackFrameInfo frameInfo) {
    return new PyStackFrame(getSession().getProject(), this, frameInfo,
                            getPositionConverter().convertFromPython(frameInfo.getPosition()));
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
      return getConnectionMessage();
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

  public int getConnectTimeout() {
    return CONNECTION_TIMEOUT;
  }


  @Nullable
  private XSourcePosition getCurrentFrameSourcePosition() {
    try {
      PyStackFrame frame = currentFrame();

      return frame.getSourcePosition();
    }
    catch (PyDebuggerException e) {
      return null;
    }
  }

  public Project getProject() {
    return getSession().getProject();
  }

  @Nullable
  @Override
  public XSourcePosition getSourcePositionForName(String name) {
    XSourcePosition currentPosition = getCurrentFrameSourcePosition();

    final PsiFile file = getPsiFile(currentPosition);

    if (file == null) return null;

    PsiElement currentElement = file.findElementAt(currentPosition.getOffset());

    if (currentElement == null) {
      return null;
    }

    final Ref<PsiElement> elementRef = Ref.create();

    PyResolveUtil.scopeCrawlUp(new PsiScopeProcessor() {
      @Override
      public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
        if (!(element instanceof PyImportElement)) {
          if (elementRef.isNull()) {
            elementRef.set(element);
          }
        }
        return false;
      }

      @Nullable
      @Override
      public <T> T getHint(@NotNull Key<T> hintKey) {
        return null;
      }

      @Override
      public void handleEvent(@NotNull Event event, @Nullable Object associated) {

      }
    }, currentElement, name, null);

    return elementRef.isNull() ? null
                               : XSourcePositionImpl.createByElement(elementRef.get());
  }

  @Nullable
  private PsiFile getPsiFile(XSourcePosition currentPosition) {
    if (currentPosition == null) {
      return null;
    }

    return PsiManager.getInstance(getProject()).findFile(currentPosition.getFile());
  }


  @Nullable
  @Override
  public XSourcePosition getSourcePositionForType(String typeName) {
    XSourcePosition currentPosition = getCurrentFrameSourcePosition();

    final PsiFile file = getPsiFile(currentPosition);

    if (file == null) return null;


    PyType type = PyTypeParser.getTypeByName(file, typeName);

    if (type instanceof PyClassType) {
      return XSourcePositionImpl.createByElement(((PyClassType)type).getPyClass());
    }

    return null;
  }
}
