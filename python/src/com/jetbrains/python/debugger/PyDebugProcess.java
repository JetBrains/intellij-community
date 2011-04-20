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
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointHandler;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.jetbrains.python.console.pydev.PydevCompletionVariant;
import com.jetbrains.python.debugger.pydev.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.ServerSocket;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static javax.swing.SwingUtilities.invokeLater;

/**
 * @author yole
 */
// todo: bundle messages
// todo: pydevd supports module reloading - look for a way to use the feature
// todo: smart step into
public class PyDebugProcess extends XDebugProcess implements IPyDebugProcess, ProcessListener {
  private final RemoteDebugger myDebugger;
  private final XBreakpointHandler[] myBreakpointHandlers;
  private final PyDebuggerEditorsProvider myEditorsProvider;
  private final ProcessHandler myProcessHandler;
  private final ExecutionConsole myExecutionConsole;
  private final Map<PySourcePosition, XLineBreakpoint> myRegisteredBreakpoints = new ConcurrentHashMap<PySourcePosition, XLineBreakpoint>();
  private final Map<String, XBreakpoint<PyExceptionBreakpointProperties>> myRegisteredExceptionBreakpoints =
    new ConcurrentHashMap<String, XBreakpoint<PyExceptionBreakpointProperties>>();

  private final List<PyThreadInfo> mySuspendedThreads = Lists.newArrayList();
  private final Map<String, XValueChildrenList> myStackFrameCache = Maps.newHashMap();
  private final Map<String, PyDebugValue> myNewVariableValue = Maps.newHashMap();

  private boolean myClosing = false;

  private PyPositionConverter myPositionConverter;

  public PyDebugProcess(final @NotNull XDebugSession session,
                        @NotNull final ServerSocket serverSocket,
                        @NotNull final ExecutionConsole executionConsole,
                        @Nullable final ProcessHandler processHandler) {
    super(session);
    session.setPauseActionSupported(true);
    myDebugger = new RemoteDebugger(this, serverSocket, 10);
    myBreakpointHandlers = new XBreakpointHandler[]{new PyLineBreakpointHandler(this), new PyExceptionBreakpointHandler(this), new DjangoLineBreakpointHandler(this)};
    myEditorsProvider = new PyDebuggerEditorsProvider();
    myProcessHandler = processHandler;
    myExecutionConsole = executionConsole;
    if (myProcessHandler != null) {
      myProcessHandler.addProcessListener(this);
    }
    myPositionConverter = new PyLocalPositionConverter();
    myDebugger.addCloseListener(new RemoteDebuggerCloseListener() {
      @Override
      public void closed() {
        session.stop();
      }
    });
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
  public void sessionInitialized() {
    super.sessionInitialized();
    ProgressManager.getInstance().run(new Task.Backgroundable(null, "Connecting to debugger", false) {
      public void run(@NotNull final ProgressIndicator indicator) {
        indicator.setText("Connecting to debugger...");
        try {
          myDebugger.waitForConnect();
          handshake();
          getSession().rebuildViews();
          registerBreakpoints();
          new RunCommand(myDebugger).execute();
        }
        catch (final Exception e) {
          myProcessHandler.destroyProcess();
          if (!myClosing) {
            invokeLater(new Runnable() {
              public void run() {
                Messages.showErrorDialog("Unable to establish connection with debugger:\n" + e.getMessage(), "Connecting to debugger");
              }
            });
          }
        }
      }
    });
  }

  private void handshake() throws PyDebuggerException {
    String remoteVersion = myDebugger.handshake();
    String currentBuild = ApplicationInfo.getInstance().getBuild().asStringWithoutProductCode();
    if ("@@BUILD_NUMBER@@".equals(remoteVersion)) {
      remoteVersion = currentBuild;
    }
    if (remoteVersion.startsWith("PY-")) {
      remoteVersion = remoteVersion.substring(3);
    }
    printToConsole("Connected to pydev debugger (build " + remoteVersion + ")\n", ConsoleViewContentType.SYSTEM_OUTPUT);

    if (!remoteVersion.equals(currentBuild)) {
      printToConsole("Warning: wrong debugger version. Use pycharm-debugger.egg from PyCharm installation folder.\n",
                     ConsoleViewContentType.ERROR_OUTPUT);
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
    for (XBreakpoint<PyExceptionBreakpointProperties> bp : myRegisteredExceptionBreakpoints.values()) {
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
    resume(ResumeCommand.Mode.STEP_OVER);
  }

  @Override
  public void startStepInto() {
    resume(ResumeCommand.Mode.STEP_INTO);
  }

  @Override
  public void startStepOut() {
    resume(ResumeCommand.Mode.STEP_OUT);
  }

  @Override
  public void stop() {
    myDebugger.disconnect();
  }

  @Override
  public void resume() {
    resume(ResumeCommand.Mode.RESUME);
  }

  @Override
  public void startPausing() {
    if (myDebugger.isConnected()) {
      myDebugger.suspendAllThreads();
    }
  }

  private void resume(final ResumeCommand.Mode mode) {
    dropFrameCaches();
    if (myDebugger.isConnected()) {
      for (PyThreadInfo suspendedThread : mySuspendedThreads) {
        final ResumeCommand command = new ResumeCommand(myDebugger, suspendedThread.getId(), mode);
        myDebugger.execute(command);
      }
    }
  }

  @Override
  public void runToPosition(@NotNull final XSourcePosition position) {
    dropFrameCaches();
    if (myDebugger.isConnected() && !mySuspendedThreads.isEmpty()) {
      final PySourcePosition pyPosition = myPositionConverter.convert(position);
      final SetBreakpointCommand command =
        new SetBreakpointCommand(myDebugger, PyLineBreakpointType.ID, pyPosition.getFile(), pyPosition.getLine());
      myDebugger.execute(command);  // set temp. breakpoint
      resume(ResumeCommand.Mode.RESUME);
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
    if (!myDebugger.isConnected()) {
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
    if (myDebugger.isConnected()) {
      final SetBreakpointCommand command =
        new SetBreakpointCommand(myDebugger, breakpoint.getType().getId(), position.getFile(), position.getLine(),
                                 breakpoint.getCondition(),
                                 breakpoint.getLogExpression());
      myDebugger.execute(command);
    }
  }

  public void removeBreakpoint(final PySourcePosition position) {
    XLineBreakpoint breakpoint = myRegisteredBreakpoints.get(position);
    if (breakpoint != null) {
      myRegisteredBreakpoints.remove(position);
      if (myDebugger.isConnected()) {
        final RemoveBreakpointCommand command = new RemoveBreakpointCommand(myDebugger, breakpoint.getType().getId(), position.getFile(), position.getLine());
        myDebugger.execute(command);
      }
    }
  }

  public void addExceptionBreakpoint(XBreakpoint<PyExceptionBreakpointProperties> breakpoint) {
    myRegisteredExceptionBreakpoints.put(breakpoint.getProperties().getException(), breakpoint);
    if (myDebugger.isConnected()) {
      final ExceptionBreakpointCommand command =
        ExceptionBreakpointCommand.addExceptionBreakpointCommand(myDebugger, breakpoint.getProperties().getException(),
                                                                 new AddExceptionBreakpointCommand.ExceptionBreakpointNotifyPolicy(
                                                                   breakpoint.getProperties().isNotifyAlways(),
                                                                   breakpoint.getProperties().isNotifyOnTerminate()));
      myDebugger.execute(command);
    }
  }

  public void removeExceptionBreakpoint(XBreakpoint<PyExceptionBreakpointProperties> breakpoint) {
    myRegisteredExceptionBreakpoints.remove(breakpoint.getProperties().getException());
    if (myDebugger.isConnected()) {
      final ExceptionBreakpointCommand command =
        ExceptionBreakpointCommand.removeExceptionBreakpointCommand(myDebugger, breakpoint.getProperties().getException());
      myDebugger.execute(command);
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
            final RemoveBreakpointCommand command = new RemoveBreakpointCommand(myDebugger, "all", position.getFile(), position.getLine());
            myDebugger.execute(command);  // remove temp. breakpoint
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
    if (myDebugger.isConnected()) {
      dropFrameCaches();
      final PyStackFrame frame = currentFrame();
      final GetCompletionsCommand command = new GetCompletionsCommand(myDebugger, frame.getThreadId(), frame.getFrameId(), prefix);
      myDebugger.execute(command);
      return command.getCompletions();
    }
    return Lists.newArrayList();
  }

  @Override
  public void startNotified(ProcessEvent event) {
  }

  @Override
  public void processTerminated(ProcessEvent event) {
  }

  @Override
  public void processWillTerminate(ProcessEvent event, boolean willBeDestroyed) {
    myClosing = true;
    myDebugger.close();
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
    else if (myDebugger.isConnected()) {
      return XDebuggerBundle.message("debugger.state.message.connected");
    }
    else {
      return "Waiting for connection...";
    }
  }
}
