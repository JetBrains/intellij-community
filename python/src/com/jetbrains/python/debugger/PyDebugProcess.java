// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger;

import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.NlsContexts.ProgressText;
import com.intellij.openapi.util.NlsContexts.ProgressTitle;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.remote.RemoteProcessControl;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.*;
import com.intellij.xdebugger.breakpoints.*;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.frame.*;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.stepping.XSmartStepIntoHandler;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.console.PythonConsoleView;
import com.jetbrains.python.console.PythonDebugLanguageConsoleView;
import com.jetbrains.python.console.pydev.PydevCompletionVariant;
import com.jetbrains.python.debugger.containerview.PyViewNumericContainerAction;
import com.jetbrains.python.debugger.pydev.*;
import com.jetbrains.python.debugger.pydev.dataviewer.DataViewerCommandBuilder;
import com.jetbrains.python.debugger.pydev.dataviewer.DataViewerCommandResult;
import com.jetbrains.python.debugger.settings.PyDebuggerSettings;
import com.jetbrains.python.debugger.smartstepinto.PySmartStepIntoContext;
import com.jetbrains.python.debugger.smartstepinto.PySmartStepIntoHandler;
import com.jetbrains.python.debugger.smartstepinto.PySmartStepIntoVariant;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.*;
import com.jetbrains.python.testing.AbstractPythonTestRunConfiguration;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;


public class PyDebugProcess extends XDebugProcess implements IPyDebugProcess, ProcessListener {

  private static final Logger LOG = Logger.getInstance(PyDebugProcess.class);
  private static final int CONNECTION_TIMEOUT = 60000;

  public static final NotificationGroup NOTIFICATION_GROUP =
    NotificationGroup.toolWindowGroup("Python Debugger", ToolWindowId.DEBUG, true, PyBundle.message("debug.notification.group"));

  private final ProcessDebugger myDebugger;
  private final XBreakpointHandler[] myBreakpointHandlers;
  private final PyDebuggerEditorsProvider myEditorsProvider;
  private final ProcessHandler myProcessHandler;
  private final ExecutionConsole myExecutionConsole;
  private final Map<PySourcePosition, XLineBreakpoint> myRegisteredBreakpoints = new ConcurrentHashMap<>();
  private final Map<String, XBreakpoint<? extends ExceptionBreakpointProperties>> myRegisteredExceptionBreakpoints =
    new ConcurrentHashMap<>();

  private final List<PyThreadInfo> mySuspendedThreads = Collections.synchronizedList(new ArrayList<>());
  private final Map<String, XValueChildrenList> myStackFrameCache = Maps.newConcurrentMap();
  private final Object myFrameCacheObject = new Object();
  private final Map<String, PyDebugValue> myNewVariableValue = Maps.newHashMap();
  private boolean myDownloadSources = false;

  protected PyPositionConverter myPositionConverter;
  @NotNull private final XSmartStepIntoHandler<?> mySmartStepIntoHandler;
  private boolean myWaitingForConnection = false;
  private PyStackFrame myConsoleContextFrame = null;
  private PyReferrersLoader myReferrersProvider;
  private final List<PyFrameListener> myFrameListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private boolean isCythonWarningShown = false;
  @Nullable private XCompositeNode myCurrentRootNode;

  private final Map<String, Map<String, PyDebugValueDescriptor>> myDescriptorsCache = Maps.newConcurrentMap();

  private final AtomicBoolean myInlayForFailedTestIsAlreadyShownSentinel = new AtomicBoolean();

  public PyDebugProcess(@NotNull XDebugSession session,
                        @NotNull ServerSocket serverSocket,
                        @NotNull ExecutionConsole executionConsole,
                        @Nullable ProcessHandler processHandler, boolean multiProcess) {
    this(session, multiProcess ? process -> process.createMultiprocessDebugger(serverSocket)
                               : process -> new RemoteDebugger(process, serverSocket, process.getConnectTimeout()),
         executionConsole, processHandler);
  }

  public PyDebugProcess(final @NotNull XDebugSession session,
                        @NotNull final ExecutionConsole executionConsole,
                        @Nullable final ProcessHandler processHandler,
                        @NotNull String serverHost, int serverPort) {
    this(session, process -> new ClientModeMultiProcessDebugger(process, serverHost, serverPort), executionConsole, processHandler);
  }

  private PyDebugProcess(@NotNull XDebugSession session,
                         @NotNull DebuggerFactory debuggerFactory,
                         @NotNull ExecutionConsole executionConsole,
                         @Nullable ProcessHandler processHandler) {
    super(session);

    session.setPauseActionSupported(true);

    myDebugger = debuggerFactory.createDebugger(this);

    List<XBreakpointHandler> breakpointHandlers = new ArrayList<>();
    breakpointHandlers.add(new PyLineBreakpointHandler(this));
    breakpointHandlers.add(new PyExceptionBreakpointHandler(this));
    for (PyBreakpointHandlerFactory factory : PyBreakpointHandlerFactory.EP_NAME.getExtensionList()) {
      breakpointHandlers.add(factory.createBreakpointHandler(this));
    }
    myBreakpointHandlers = breakpointHandlers.toArray(XBreakpointHandler.EMPTY_ARRAY);

    myEditorsProvider = new PyDebuggerEditorsProvider();
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

    mySmartStepIntoHandler = new PySmartStepIntoHandler(this);

    PyDebugValueExecutionService executionService = PyDebugValueExecutionService.getInstance(getProject());
    executionService.sessionStarted(this);
    session.addSessionListener(new XDebugSessionListener() {
      @Override
      public void sessionStopped() {
        executionService.sessionStopped(PyDebugProcess.this);
      }
    });

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

    session.addSessionListener(new XDebugSessionListener() {
      @Override
      public void stackFrameChanged() {
        String currentFrameThreadId = null;
        final XStackFrame currentFrame = session.getCurrentStackFrame();
        if (currentFrame instanceof PyStackFrame) {
          currentFrameThreadId = ((PyStackFrame)currentFrame).getThreadId();
        }
        final XExecutionStack activeStack = session.getSuspendContext().getActiveExecutionStack();
        if ((activeStack == null) || (currentFrameThreadId == null)) {
          return;
        }
        final XStackFrame frameFromSuspendContext = activeStack.getTopFrame();
        String activeStackThreadId = null;
        if (frameFromSuspendContext instanceof PyStackFrame) {
          activeStackThreadId = ((PyStackFrame)frameFromSuspendContext).getThreadId();
        }
        if (!currentFrameThreadId.equals(activeStackThreadId)) {
          // another thread was selected, we should update suspendContext
          PyThreadInfo threadInfo = null;
          for (PyThreadInfo info : mySuspendedThreads) {
            if (info.getId().equals(currentFrameThreadId)) {
              threadInfo = info;
              break;
            }
          }
          if (threadInfo != null) {
            getSession().positionReached(createSuspendContext(threadInfo));
          }
        }
        for (PyFrameListener listener : myFrameListeners) {
          listener.frameChanged();
        }
      }

      @Override
      public void sessionStopped() {
        XDebugSessionListener.super.sessionStopped();
        for (PyFrameListener listener : myFrameListeners) {
          listener.sessionStopped();
        }
      }
    });

    session.addSessionListener(new XDebugSessionListener() {
      @Override
      public void sessionStopped() {
        ApplicationManager.getApplication().invokeLater(() -> WriteAction.run(() -> {
          PyUnitTestsDebuggingService.removeInlaysAssociatedWithSession(session);
        }));
      }
    });
  }

  private MultiProcessDebugger createMultiprocessDebugger(ServerSocket serverSocket) {
    boolean useDispatcher = Registry.get("python.debugger.use.dispatcher").asBoolean();
    MultiProcessDebugger debugger = new MultiProcessDebugger(this, serverSocket, getConnectTimeout(), useDispatcher);
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

  @Override
  public XBreakpointHandler<?> @NotNull [] getBreakpointHandlers() {
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
  @NotNull
  public XSmartStepIntoHandler<?> getSmartStepIntoHandler() {
    return mySmartStepIntoHandler;
  }

  @Override
  public void sessionInitialized() {
    waitForConnection(getConnectionMessage(), getConnectionTitle());
  }

  protected void waitForConnection(final @ProgressText String connectionMessage, @ProgressTitle String connectionTitle) {
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
          if (shouldLogConnectionException(e)) {
            NOTIFICATION_GROUP
              .createNotification(PyBundle.message("debug.notification.title.connection.failed"), e.getMessage(), NotificationType.ERROR)
              .notify(myProject);
          }
        }
      }
    });
  }

  protected boolean shouldLogConnectionException(final Exception e) {
    return true;
  }

  @Override
  public void init() {
    getSession().rebuildViews();
    registerBreakpoints();
    setShowReturnValues(PyDebuggerSettings.getInstance().isWatchReturnValues());
    setUnitTestDebuggingMode();
  }

  @Override
  public int handleDebugPort(int localPort) throws IOException {
    if (myProcessHandler instanceof RemoteProcessControl) {
      return getRemoteHostPortForDebuggerConnection(localPort, (RemoteProcessControl)myProcessHandler).getSecond();
    }
    else {
      return localPort;
    }
  }

  protected static Pair<String, Integer> getRemoteHostPortForDebuggerConnection(int localPort, @NotNull RemoteProcessControl handler)
    throws IOException {
    try {
      return handler.getRemoteSocket(localPort);
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
      PythonDebugLanguageConsoleView consoleView = (PythonDebugLanguageConsoleView)myExecutionConsole;
      UIUtil.invokeLaterIfNeeded(() -> {
        consoleView.enableConsole(false);
        consoleView.getPydevConsoleView().setConsoleEnabled(true);
      });
    }
  }

  @Override
  public void consoleInputRequested(boolean isStarted) {
    if (myExecutionConsole instanceof PythonDebugLanguageConsoleView) {
      PythonConsoleView consoleView = ((PythonDebugLanguageConsoleView)myExecutionConsole).getPydevConsoleView();
      if (isStarted) {
        consoleView.inputRequested();
      }
      else {
        consoleView.inputReceived();
      }
    }
  }

  @Override
  public void showWarning(String warningId) {
    if (warningId.equals("cython")) {
      if (!isCythonWarningShown) {
        PyCythonExtensionWarning.showCythonExtensionWarning(getSession().getProject());
        isCythonWarningShown = true;
      }
    }
  }

  protected void afterConnect() {
  }

  protected void beforeConnect() {
  }

  protected @ProgressText String getConnectionMessage() {
    return PyBundle.message("python.debugger.settings.waiting.for.connection");
  }

  protected @ProgressTitle String getConnectionTitle() {
    return PyBundle.message("python.debugger.settings.connecting.to.debugger");
  }

  private void handshake() throws PyDebuggerException {
    String remoteVersion = myDebugger.handshake();
    String currentBuild = ApplicationInfo.getInstance().getBuild().asStringWithoutProductCode();
    if ("@@BUILD_NUMBER@@".equals(remoteVersion)) {
      remoteVersion = currentBuild;
    }
    else {
      remoteVersion = StringUtil.trimStart(remoteVersion, "PY-");
    }
    printToConsole(PyBundle.message("debugger.connected.to.pydev.debugger.build", remoteVersion), ConsoleViewContentType.SYSTEM_OUTPUT);

    if (!remoteVersion.startsWith(currentBuild)) {
      LOG.warn(String.format("Wrong debugger version. Remote version: %s Current build: %s", remoteVersion, currentBuild));
      printToConsole(PyBundle.message("debugger.warning.wrong.debugger.version", currentBuild), ConsoleViewContentType.ERROR_OUTPUT);
    }
  }

  @Override
  public void printToConsole(@Nls String text, ConsoleViewContentType contentType) {
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
  public void registerAdditionalActions(@NotNull DefaultActionGroup leftToolbar,
                                        @NotNull DefaultActionGroup topToolbar,
                                        @NotNull DefaultActionGroup settings) {
    super.registerAdditionalActions(leftToolbar, topToolbar, settings);
    settings.add(new WatchReturnValuesAction(this));
    settings.add(new PyVariableViewSettings.SimplifiedView(this));
    settings.add(new PyVariableViewSettings.VariablesPolicyGroup());
  }

  private static class WatchReturnValuesAction extends ToggleAction {
    private volatile boolean myWatchesReturnValues;
    private final PyDebugProcess myProcess;
    private final @NlsActions.ActionText String myText;

    WatchReturnValuesAction(@NotNull PyDebugProcess debugProcess) {
      super("", PyBundle.message("debugger.watch.return.values.description"), null);
      myWatchesReturnValues = PyDebuggerSettings.getInstance().isWatchReturnValues();
      myProcess = debugProcess;
      myText = PyBundle.message("debugger.watch.show.return.values");
    }

    @Override
    public void update(@NotNull final AnActionEvent e) {
      super.update(e);
      final Presentation presentation = e.getPresentation();
      presentation.setEnabled(true);
      presentation.setText(myText);
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return myWatchesReturnValues;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean watch) {
      myWatchesReturnValues = watch;
      PyDebuggerSettings.getInstance().setWatchReturnValues(watch);
      final Project project = e.getProject();
      if (project != null) {
        myProcess.setShowReturnValues(myWatchesReturnValues);
        myProcess.getSession().rebuildViews();
      }
    }
  }

  public void setShowReturnValues(boolean showReturnValues) {
    myDebugger.setShowReturnValues(showReturnValues);
  }

  public void setUnitTestDebuggingMode() {
    ExecutionEnvironment environment = ((XDebugSessionImpl)getSession()).getExecutionEnvironment();
    if (environment == null) return;
    RunProfile runProfile = environment.getRunProfile();
    if (runProfile instanceof AbstractPythonTestRunConfiguration
        && PyDebuggerOptionsProvider.getInstance(getProject()).isDropIntoDebuggerOnFailedTest()) {
      myDebugger.setUnitTestDebuggingMode();
    }
  }

  @Override
  public void startStepOver(@Nullable XSuspendContext context) {
    passToCurrentThread(context, ResumeOrStepCommand.Mode.STEP_OVER);
  }

  @Override
  public void startStepInto(@Nullable XSuspendContext context) {
    passToCurrentThread(context, ResumeOrStepCommand.Mode.STEP_INTO);
  }

  public void startStepIntoMyCode(@Nullable XSuspendContext context) {
    if (!checkCanPerformCommands()) return;
    getSession().sessionResumed();
    passToCurrentThread(context, ResumeOrStepCommand.Mode.STEP_INTO_MY_CODE);
  }

  public void startSetNextStatement(@Nullable XSuspendContext context,
                                    @NotNull XSourcePosition sourcePosition,
                                    @NotNull PyDebugCallback<Pair<Boolean, String>> callback) {
    if (!checkCanPerformCommands()) return;
    dropFrameCaches();
    if (isConnected()) {
      String threadId = threadIdBeforeResumeOrStep(context);
      for (PyThreadInfo suspendedThread : mySuspendedThreads) {
        if (threadId != null && threadId.equals(suspendedThread.getId())) {
          myDebugger.setNextStatement(threadId, sourcePosition, getFunctionName(sourcePosition), callback);
          break;
        }
      }
    }
  }

  @Override
  public void startStepOut(@Nullable XSuspendContext context) {
    passToCurrentThread(context, ResumeOrStepCommand.Mode.STEP_OUT);
  }

  public void startSmartStepInto(@NotNull PySmartStepIntoVariant variant) {
    String threadId = variant.getContext().getFrame().getThreadId();
    String frameId = variant.getContext().getFrame().getFrameId();
    if (isConnected()) {
      dropFrameCaches();
      for (PyThreadInfo suspendedThread : mySuspendedThreads) {
        if (threadId.equals(suspendedThread.getId())) {
          PySmartStepIntoContext context = variant.getContext();
          myDebugger.smartStepInto(threadId, frameId, variant.getFunctionName(), variant.getCallOrder(),
                                   context.getStartLine(), context.getEndLine());
          break;
        }
      }
    }
  }

  public List<Pair<String, Boolean>> getSmartStepIntoVariants(int startContextLine, int endContextLine) {
    try {
      PyStackFrame frame = currentFrame();
      return myDebugger.getSmartStepIntoVariants(frame.getThreadId(), frame.getFrameId(), startContextLine, endContextLine);
    }
    catch (PyDebuggerException e) {
      return Collections.emptyList();
    }
  }

  @Override
  public void stop() {
    myDebugger.close();
  }

  @Override
  public void resume(@Nullable XSuspendContext context) {
    passResumeToAllThreads();
  }

  @Override
  public void startPausing() {
    if (isConnected()) {
      myDebugger.suspendAllThreads();
    }
  }

  @Override
  public void suspendAllOtherThreads(PyThreadInfo thread) {
    myDebugger.suspendOtherThreads(thread);
  }

  /**
   * Check if there is the thread suspended on the breakpoint with "Suspend all" policy
   *
   * @return true if this thread exists
   */
  @Override
  public boolean isSuspendedOnAllThreadsPolicy() {
    if (getSession().isSuspended()) {
      for (PyThreadInfo threadInfo : getThreads()) {
        final List<PyStackFrameInfo> frames = threadInfo.getFrames();
        if ((threadInfo.getState() == PyThreadInfo.State.SUSPENDED) && (frames != null)) {
          XBreakpoint<?> breakpoint = null;
          if (threadInfo.isStopOnBreakpoint()) {
            final PySourcePosition position = frames.get(0).getPosition();
            breakpoint = myRegisteredBreakpoints.get(position);
          }
          else if (threadInfo.isExceptionBreak()) {
            String exceptionName = threadInfo.getMessage();
            if (exceptionName != null) {
              breakpoint = myRegisteredExceptionBreakpoints.get(exceptionName);
            }
          }
          if ((breakpoint != null) && (breakpoint.getType().isSuspendThreadSupported()) &&
              (breakpoint.getSuspendPolicy() == SuspendPolicy.ALL)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private void passResumeToAllThreads() {
    dropFrameCaches();
    myInlayForFailedTestIsAlreadyShownSentinel.set(false);
    if (isConnected()) {
      for (PyThreadInfo thread : myDebugger.getThreads()) {
        myDebugger.resumeOrStep(thread.getId(), ResumeOrStepCommand.Mode.RESUME);
      }
    }
  }

  private void passToCurrentThread(@Nullable XSuspendContext context, final ResumeOrStepCommand.Mode mode) {
    dropFrameCaches();
    if (isConnected()) {
      String threadId = threadIdBeforeResumeOrStep(context);

      for (PyThreadInfo suspendedThread : mySuspendedThreads) {
        if (threadId == null || threadId.equals(suspendedThread.getId())) {
          myDebugger.resumeOrStep(suspendedThread.getId(), mode);
          break;
        }
      }
    }
  }

  @Nullable
  private static String threadIdBeforeResumeOrStep(@Nullable XSuspendContext context) {
    if (context instanceof PySuspendContext) {
      return ((PySuspendContext)context).getActiveExecutionStack().getThreadId();
    }
    else {
      return null;
    }
  }

  public boolean isConnected() {
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
  public void runToPosition(@NotNull final XSourcePosition position, @Nullable XSuspendContext context) {
    dropFrameCaches();
    if (isConnected() && !mySuspendedThreads.isEmpty()) {
      final PySourcePosition pyPosition = myPositionConverter.convertToPython(position);
      String type =
        ReadAction.compute(() -> {
          String t = PyLineBreakpointType.ID;
          final Document document = FileDocumentManager.getInstance().getDocument(position.getFile());
          if (document != null) {
            for (XBreakpointType breakpointType : XBreakpointType.EXTENSION_POINT_NAME.getExtensionList()) {
              if (breakpointType instanceof PyBreakpointType &&
                  ((PyBreakpointType)breakpointType).canPutInDocument(getSession().getProject(), document)) {
                t = breakpointType.getId();
                break;
              }
            }
          }
          return t;
        });
      myDebugger.setTempBreakpoint(type, pyPosition.getFile(), pyPosition.getLine());

      passToCurrentThread(context, ResumeOrStepCommand.Mode.RESUME);
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
  public String execTableCommand(String command, TableCommandType commandType) throws PyDebuggerException {
    final PyStackFrame frame = currentFrame();
    return myDebugger.execTableCommand(frame.getThreadId(), frame.getFrameId(), command, commandType);
  }

  @Override
  public boolean isFrameCached(@NotNull XStackFrame contextFrame) {
    synchronized (myFrameCacheObject) {
      final PyStackFrame frame = (PyStackFrame)contextFrame;
      return myStackFrameCache.containsKey(frame.getThreadFrameId());
    }
  }

  @Override
  @Nullable
  public XValueChildrenList loadFrame(@Nullable final XStackFrame contextFrame) throws PyDebuggerException {
    final PyStackFrame frame = contextFrame == null ? currentFrame() : (PyStackFrame)contextFrame;
    synchronized (myFrameCacheObject) {
      //do not reload frame every time it is needed, because due to bug in pdb, reloading frame clears all variable changes
      if (!myStackFrameCache.containsKey(frame.getThreadFrameId())) {
        XValueChildrenList values = myDebugger.loadFrame(frame.getThreadId(), frame.getFrameId());
        myStackFrameCache.put(frame.getThreadFrameId(), values);
      }
      showFailedTestInfoIfNecessary(frame);
    }
    return applyNewValue(getFrameFromCache(frame), frame.getThreadFrameId());
  }

  private void showFailedTestInfoIfNecessary(@NotNull PyStackFrame frame) throws PyDebuggerException {
    PyExecutionStack pyExecutionStack = null;
    XDebugSession session = getSession();
    if (session != null) {
      XSuspendContext suspendContext = session.getSuspendContext();
      if (suspendContext != null) {
        XExecutionStack executionStack = suspendContext.getActiveExecutionStack();
        pyExecutionStack = executionStack != null ? (PyExecutionStack)executionStack : null;
      }
    }

    if (pyExecutionStack == null || !isFailedTestStop(pyExecutionStack.getThreadInfo())) return;

    XValueChildrenList values = getFrameFromCache(frame);
    if (values == null) return;

    if (!myInlayForFailedTestIsAlreadyShownSentinel.getAndSet(true)) {
      PyDebugValue exceptionDataContainer = getExceptionDataFromFrame(values);
      if (exceptionDataContainer == null) return;

      XValueChildrenList exceptionDataValues = loadVariable(exceptionDataContainer);
      if (exceptionDataValues.size() < 2) return;

      PyDebugValue exceptionData = ((PyDebugValue)exceptionDataValues.getValue(1));
      String exceptionType = exceptionData.getType();
      String errorMessage = exceptionData.getValue();
      if (exceptionType == null || errorMessage == null) return;

      if (exceptionType.equals("EqualsAssertionError") && errorMessage.startsWith(" :: ")) {
        errorMessage = errorMessage.replaceFirst(" :: ", "");
      }

      PyThreadInfo threadInfo = pyExecutionStack.getThreadInfo();
      List<PyStackFrameInfo> threadFrames = threadInfo.getFrames();
      boolean isTestSetUpFail = false;
      if (threadFrames != null &&
          (threadFrames.size() == 1 || threadFrames.size() > 1 && PyUnitTestsDebuggingService.isErrorInTestSetUpOrTearDown(threadFrames))) {
        isTestSetUpFail = true;
      }
      getProject().getService(PyUnitTestsDebuggingService.class).showFailedTestInlay(
        session, frame, exceptionType, errorMessage, isTestSetUpFail);
    }
  }

  @Override
  public void loadAsyncVariablesValues(@Nullable XStackFrame contextFrame, @NotNull final List<PyAsyncValue<String>> pyAsyncValues) {
    PyDebugValueExecutionService.getInstance(getProject()).submitTask(this, () -> {
      try {
        if (isConnected()) {
          final PyStackFrame frame = contextFrame == null ? currentFrame() : (PyStackFrame)contextFrame;
          XSuspendContext context = getSession().getSuspendContext();
          String threadId = threadIdBeforeResumeOrStep(context);
          for (PyThreadInfo suspendedThread : mySuspendedThreads) {
            if (threadId == null || threadId.equals(suspendedThread.getId())) {
              myDebugger.loadFullVariableValues(frame.getThreadId(), frame.getFrameId(), pyAsyncValues);
              break;
            }
          }
        }
      }
      catch (PyDebuggerException e) {
        if (!isConnected()) return;
        for (PyAsyncValue<String> asyncValue : pyAsyncValues) {
          PyDebugValue value = asyncValue.getDebugValue();
          for (XValueNode node : value.getValueNodes()) {
            if (node != null && !node.isObsolete()) {
              if (e.getMessage().startsWith("Timeout")) {
                value.updateNodeValueAfterLoading(node, " ", "", PyBundle.message("debugger.variables.view.loading.timed.out"));
                PyVariableViewSettings.showWarningMessage(getCurrentRootNode());
              }
              else {
                LOG.error(e);
              }
            }
          }
        }
      }
    });
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
    PyDebugValue debugValue = new PyDebugValue(var, var.getFullName());
    return myDebugger.loadVariable(frame.getThreadId(), frame.getFrameId(), debugValue);
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

  @Override
  public DataViewerCommandResult executeDataViewerCommand(DataViewerCommandBuilder builder) throws PyDebuggerException {
    final PyStackFrame frame = currentFrame();
    builder.setThreadId(frame.getThreadId());
    builder.setFrameId(frame.getFrameId());
    return myDebugger.executeDataViewerCommand(builder);
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

  @Override
  public void setCurrentRootNode(@Nullable XCompositeNode currentRootNode) {
    myCurrentRootNode = currentRootNode;
  }

  @Nullable
  @Override
  public XCompositeNode getCurrentRootNode() {
    return myCurrentRootNode;
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
      throw new PyDebuggerException(PyBundle.message("debugger.debug.process.running"));
    }

    return frame;
  }

  private String getFunctionNameForBreakpoint(final XLineBreakpoint breakpoint) {
    XSourcePosition sourcePosition = breakpoint.getSourcePosition();
    return sourcePosition == null ? null : getFunctionName(sourcePosition);
  }

  @Nullable
  private String getFunctionName(@NotNull final XSourcePosition position) {
    final VirtualFile file = position.getFile();
    return ReadAction.compute(() -> {
      final Document document = FileDocumentManager.getInstance().getDocument(file);
      final Project project = getSession().getProject();
      if (document != null) {
        if (FileTypeRegistry.getInstance().isFileOfType(file, PythonFileType.INSTANCE)) {
          int breakpointLine = position.getLine();
          if (breakpointLine < document.getLineCount()) {
            PsiElement psiElement = XDebuggerUtil.getInstance().
              findContextElement(file, document.getLineStartOffset(breakpointLine), project, false);
            PyFunction function = PsiTreeUtil.getParentOfType(psiElement, PyFunction.class);
            if (function != null) {
              return function.getName();
            }
          }
        }
      }
      return null;
    });
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
      SuspendPolicy policy = breakpoint.getType().isSuspendThreadSupported() ? breakpoint.getSuspendPolicy() : SuspendPolicy.NONE;
      myDebugger.setBreakpoint(breakpoint.getType().getId(),
                               position.getFile(),
                               position.getLine(),
                               conditionExpression,
                               logExpression,
                               getFunctionNameForBreakpoint(breakpoint),
                               policy
      );
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
    myRegisteredExceptionBreakpoints.put(breakpoint.getProperties().getExceptionBreakpointId(), breakpoint);
    if (isConnected()) {
      String conditionExpression = breakpoint.getConditionExpression() == null
                                   ? null
                                   : breakpoint.getConditionExpression().getExpression();
      breakpoint.getProperties().setCondition(conditionExpression);
      String logExpression = breakpoint.getLogExpressionObject() == null
                             ? null
                             : breakpoint.getLogExpressionObject().getExpression();
      breakpoint.getProperties().setLogExpression(logExpression);
      myDebugger.addExceptionBreakpoint(breakpoint.getProperties());
    }
  }

  public void removeExceptionBreakpoint(XBreakpoint<? extends ExceptionBreakpointProperties> breakpoint) {
    myRegisteredExceptionBreakpoints.remove(breakpoint.getProperties().getExceptionBreakpointId());
    if (isConnected()) {
      myDebugger.removeExceptionBreakpoint(breakpoint.getProperties());
    }
  }

  public Collection<PyThreadInfo> getThreads() {
    return myDebugger.getThreads();
  }

  @Override
  public void threadSuspended(final PyThreadInfo threadInfo, boolean updateSourcePosition) {
    if (!mySuspendedThreads.contains(threadInfo)) {
      mySuspendedThreads.add(threadInfo);

      final List<PyStackFrameInfo> frames = threadInfo.getFrames();
      if (frames != null) {
        final PySuspendContext suspendContext = createSuspendContext(threadInfo);

        XBreakpoint<?> breakpoint = null;
        if (threadInfo.isStopOnBreakpoint()) {
          final PySourcePosition framePosition = frames.get(0).getPosition();
          PySourcePosition position = myPositionConverter.convertFrameToPython(framePosition);
          breakpoint = myRegisteredBreakpoints.get(position);
          if (breakpoint == null) {
            myDebugger.removeTempBreakpoint(position.getFile(), position.getLine());
          }
        }
        else if (threadInfo.isExceptionBreak()) {
          String exceptionName = threadInfo.getMessage();
          if (exceptionName != null) {
            breakpoint = myRegisteredExceptionBreakpoints.get(exceptionName);
          }
        }
        if (breakpoint != null) {
          if ((breakpoint.getType().isSuspendThreadSupported()) && (breakpoint.getSuspendPolicy() == SuspendPolicy.ALL)) {
            suspendAllOtherThreads(threadInfo);
          }
        }

        if (updateSourcePosition) {
          if (breakpoint != null) {
            boolean shouldSuspend = getSession().breakpointReached(breakpoint, threadInfo.getMessage(), suspendContext);
            if (!shouldSuspend) resume(suspendContext);
          }
          else {
            ((XDebugSessionImpl)getSession()).positionReached(suspendContext, isFailedTestStop(threadInfo));
          }
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
    PyDebugValueExecutionService.getInstance(getProject()).cancelSubmittedTasks(this);
  }

  @NotNull
  public List<PydevCompletionVariant> getCompletions(String prefix) throws Exception {
    if (isConnected()) {
      final PyStackFrame frame = currentFrame();
      return myDebugger.getCompletions(frame.getThreadId(), frame.getFrameId(), prefix);
    }
    return new ArrayList<>();
  }

  @NotNull
  public String getDescription(String prefix) throws Exception {
    if (isConnected()) {
      final PyStackFrame frame = currentFrame();
      return myDebugger.getDescription(frame.getThreadId(), frame.getFrameId(), prefix);
    }
    return "";
  }

  public void interruptDebugConsole() {
    if (isConnected()) {
      myDebugger.interruptDebugConsole();
    }
  }

  @Override
  public void startNotified(@NotNull ProcessEvent event) {
  }

  @Override
  public void processTerminated(@NotNull ProcessEvent event) {
    myDebugger.close();
  }

  @Override
  public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
  }

  public PyStackFrame createStackFrame(PyStackFrameInfo frameInfo) {
    final PyStackFrame frame = new PyStackFrame(getSession().getProject(), this, frameInfo,
                                                getPositionConverter().convertFromPython(frameInfo.getPosition(), frameInfo.getName()));
    frame.restoreChildrenDescriptors(myDescriptorsCache);
    return frame;
  }

  @Override
  public @ProgressText String getCurrentStateMessage() {
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
  protected XSourcePosition getCurrentFrameSourcePosition() {
    try {
      PyStackFrame frame = currentFrame();

      return frame.getSourcePosition();
    }
    catch (PyDebuggerException e) {
      return null;
    }
  }

  @Override
  public @NotNull Project getProject() {
    return getSession().getProject();
  }

  @Nullable
  @Override
  public XSourcePosition getSourcePositionForName(String name, String parentType) {
    if (name == null) return null;
    XSourcePosition currentPosition = getCurrentFrameSourcePosition();

    final PsiFile file = getPsiFile(currentPosition);

    if (file == null) return null;

    if (Strings.isNullOrEmpty(parentType)) {
      final Ref<PsiElement> elementRef = resolveInCurrentFrame(name, currentPosition, file);
      return elementRef.isNull() ? null : XDebuggerUtil.getInstance().createPositionByElement(elementRef.get());
    }
    else {
      final PyType parentDef = resolveTypeFromString(parentType, file);
      if (parentDef == null) {
        return null;
      }
      final var context = TypeEvalContext.codeInsightFallback(file.getProject());
      List<? extends RatedResolveResult> results =
        parentDef.resolveMember(name, null, AccessDirection.READ, PyResolveContext.defaultContext(context));
      if (results != null && !results.isEmpty()) {
        return XDebuggerUtil.getInstance().createPositionByElement(results.get(0).getElement());
      }
      else {
        return typeToPosition(parentDef); // at least try to return parent
      }
    }
  }


  @NotNull
  private static Ref<PsiElement> resolveInCurrentFrame(final String name, XSourcePosition currentPosition, PsiFile file) {
    final Ref<PsiElement> elementRef = Ref.create();
    PsiElement currentElement = file.findElementAt(currentPosition.getOffset());

    if (currentElement == null) {
      return elementRef;
    }


    PyResolveUtil.scopeCrawlUp(new PsiScopeProcessor() {
      @Override
      public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
        if ((element instanceof PyImportElement)) {
          PyImportElement importElement = (PyImportElement)element;
          if (name.equals(importElement.getVisibleName())) {
            if (elementRef.isNull()) {
              elementRef.set(element);
            }
            return false;
          }
          return true;
        }
        else {
          if (elementRef.isNull()) {
            elementRef.set(element);
          }
          return false;
        }
      }
    }, currentElement, name, null);
    return elementRef;
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

    if (typeName == null || !(file instanceof PyFile)) return null;


    final PyType pyType = resolveTypeFromString(typeName, file);
    return pyType == null ? null : typeToPosition(pyType);
  }

  @Override
  public void showNumericContainer(@NotNull PyDebugValue value) {
    PyViewNumericContainerAction.showNumericViewer(getProject(), value);
  }

  @Override
  public void addFrameListener(@NotNull PyFrameListener listener) {
    myFrameListeners.add(listener);
  }

  @Override
  public boolean isSimplifiedView() {
    return PyDebuggerSettings.getInstance().isSimplifiedView();
  }

  @Nullable
  private static XSourcePosition typeToPosition(PyType pyType) {
    final PyClassType classType = PyUtil.as(pyType, PyClassType.class);

    if (classType != null) {
      return XDebuggerUtil.getInstance().createPositionByElement(classType.getPyClass());
    }

    final PyModuleType moduleType = PyUtil.as(pyType, PyModuleType.class);
    if (moduleType != null) {
      return XDebuggerUtil.getInstance().createPositionByElement(moduleType.getModule());
    }
    return null;
  }

  private PyType resolveTypeFromString(String typeName, PsiFile file) {
    typeName = typeName.replace("__builtin__.", "");
    PyType pyType = null;
    if (!typeName.contains(".")) {

      pyType = PyTypeParser.getTypeByName(file, typeName);
    }
    if (pyType == null) {
      PyElementGenerator generator = PyElementGenerator.getInstance(getProject());
      PyPsiFacade psiFacade = PyPsiFacade.getInstance(getProject());
      PsiFile dummyFile = generator.createDummyFile(((PyFile)file).getLanguageLevel(), "");
      Module moduleForFile = ModuleUtilCore.findModuleForPsiElement(file);
      dummyFile.putUserData(ModuleUtilCore.KEY_MODULE, moduleForFile);

      pyType = psiFacade.parseTypeAnnotation(typeName, dummyFile);
    }
    return pyType;
  }

  boolean isFailedTestStop(@NotNull PyThreadInfo threadInfo) {
    return threadInfo.isExceptionBreak() && !myRegisteredExceptionBreakpoints.containsKey(threadInfo.getMessage());
  }

  private @Nullable XValueChildrenList getFrameFromCache(@NotNull PyStackFrame frame) {
    return myStackFrameCache.getOrDefault(frame.getThreadFrameId(), null);
  }

  private static @Nullable PyDebugValue getExceptionDataFromFrame(@NotNull XValueChildrenList values) {
    for (int i = 0; i < values.size(); i++) {
      if (values.getName(i).equals("__exception__")) {
        return (PyDebugValue)values.getValue(i);
      }
    }
    return null;
  }

  private interface DebuggerFactory {
    @NotNull
    ProcessDebugger createDebugger(@NotNull PyDebugProcess process);
  }
}
