// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.env.debug.tasks;

import com.intellij.execution.ExecutionResult;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.*;
import com.intellij.xdebugger.breakpoints.SuspendPolicy;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointManager;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.jetbrains.env.PyExecutionFixtureTestTask;
import com.jetbrains.python.console.PythonDebugLanguageConsoleView;
import com.jetbrains.python.console.pydev.PydevCompletionVariant;
import com.jetbrains.python.debugger.*;
import com.jetbrains.python.debugger.pydev.ProcessDebugger;
import com.jetbrains.python.debugger.pydev.PyDebugCallback;
import com.jetbrains.python.debugger.smartstepinto.PySmartStepIntoVariant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.concurrency.Promise;
import org.junit.Assert;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;


public abstract class PyBaseDebuggerTask extends PyExecutionFixtureTestTask {
  protected PyDebugProcess myDebugProcess;
  protected XDebugSession mySession;
  protected Semaphore myPausedSemaphore;
  protected Semaphore myTerminateSemaphore;
  protected boolean shouldPrintOutput = false;
  protected boolean myProcessCanTerminate;
  protected ExecutionResult myExecutionResult;
  protected SuspendPolicy myDefaultSuspendPolicy = SuspendPolicy.THREAD;
  protected final Logger myLogger = Logger.getInstance(PyBaseDebuggerTask.class);
  /**
   * The value must align with the one from the pydevd_resolver.py module.
   */
  protected static final int MAX_ITEMS_TO_HANDLE = 100;

  protected PyBaseDebuggerTask(@Nullable final String relativeTestDataPath) {
    super(relativeTestDataPath);
  }

  protected void waitForPause() throws InterruptedException {
    Assert.assertTrue("Debugger didn't stopped within timeout\nOutput:" + output(), waitFor(myPausedSemaphore));

    XDebuggerTestUtil.waitForSwing();
  }

  protected void waitForTerminate() throws InterruptedException {
    setProcessCanTerminate(true);

    Assert.assertTrue("Debugger didn't terminated within timeout\nOutput:" + output(), waitFor(myTerminateSemaphore));
    XDebuggerTestUtil.waitForSwing();
  }

  protected void runToLine(int line) throws InterruptedException {
    XDebugSession currentSession = XDebuggerManager.getInstance(getProject()).getCurrentSession();
    XSourcePosition position = currentSession.getCurrentPosition();

    currentSession.runToPosition(XDebuggerUtil.getInstance().createPosition(position.getFile(), line), false);

    waitForPause();
  }

  protected void resume() {
    checkSessionPaused();

    XDebuggerManager.getInstance(getProject()).getCurrentSession().resume();
  }

  protected void stepOver() {
    checkSessionPaused();

    XDebuggerManager.getInstance(getProject()).getCurrentSession().stepOver(false);
  }

  protected void stepInto() {
    checkSessionPaused();

    XDebuggerManager.getInstance(getProject()).getCurrentSession().stepInto();
  }

  @TestOnly
  protected void stepIntoMyCode() {
    checkSessionPaused();

    XDebugSession currentSession = XDebuggerManager.getInstance(getProject()).getCurrentSession();
    PyDebugProcess debugProcess = (PyDebugProcess)currentSession.getDebugProcess();
    debugProcess.startStepIntoMyCode(currentSession.getSuspendContext());
  }

  protected void smartStepInto(String funcName, int callOrder) {
    XDebugSession currentSession = XDebuggerManager.getInstance(getProject()).getCurrentSession();

    Assert.assertTrue(currentSession.isSuspended());
    Assert.assertEquals(0, myPausedSemaphore.availablePermits());

    getSmartStepIntoVariantsAsync().onSuccess(smartStepIntoVariants -> {
      ApplicationManager.getApplication().invokeAndWait(() -> {
          for (Object o : smartStepIntoVariants) {
            PySmartStepIntoVariant variant = (PySmartStepIntoVariant)o;
            if (variant.getFunctionName().equals(funcName) && variant.getCallOrder() == callOrder)
              myDebugProcess.startSmartStepInto(variant);
          }
      });
    });
  }

  protected Pair<Boolean, String> setNextStatement(int line) throws PyDebuggerException {
    XDebugSession currentSession = XDebuggerManager.getInstance(getProject()).getCurrentSession();
    XSourcePosition position = currentSession.getCurrentPosition();
    EvaluationCallback<Pair<Boolean, String>> callback = new EvaluationCallback<>();

    myDebugProcess.startSetNextStatement(
      currentSession.getSuspendContext(),
      XDebuggerUtil.getInstance().createPosition(position.getFile(), line),
      new PyDebugCallback<>() {
        @Override
        public void ok(Pair<Boolean, String> value) {
          callback.evaluated(value);
        }

        @Override
        public void error(PyDebuggerException exception) {
          callback.errorOccurred(exception.getMessage());
        }
      }
    );

    Pair<Pair<Boolean, String>, String> result = callback.waitFor(NORMAL_TIMEOUT);
    if (result.second != null) {
      throw new PyDebuggerException(result.second);
    }

    return result.first;
  }

  protected List<PyDebugValue> loadChildren(List<PyDebugValue> debugValues, String name) throws PyDebuggerException {
    PyDebugValue var = findDebugValueByName(debugValues, name);
    return convertToList(myDebugProcess.loadVariable(var));
  }

  protected XValueChildrenList loadVariable(PyDebugValue var) throws PyDebuggerException {
    return myDebugProcess.loadVariable(var);
  }

  protected List<PyDebugValue> loadFrame() throws PyDebuggerException {
    return convertToList(myDebugProcess.loadFrame(null));
  }

  protected List<PyDebugValue> loadSpecialVariables(ProcessDebugger.GROUP_TYPE groupType) throws PyDebuggerException {
    return convertToList(myDebugProcess.loadSpecialVariables(groupType));
  }

  protected PyStackFrame getCurrentStackFrame() {
    return (PyStackFrame) myDebugProcess.getSession().getCurrentStackFrame();
  }

  protected String computeValueAsync(List<PyDebugValue> debugValues, String name) throws PyDebuggerException, InterruptedException {
    final PyDebugValue debugValue = findDebugValueByName(debugValues, name);
    assert debugValue != null;
    Semaphore variableSemaphore = new Semaphore(0);
    final ArrayList<PyFrameAccessor.PyAsyncValue<String>> valuesForEvaluation = createAsyncValue(debugValue, variableSemaphore);
    myDebugProcess.loadAsyncVariablesValues(null, valuesForEvaluation);
    if (!variableSemaphore.tryAcquire(NORMAL_TIMEOUT, TimeUnit.MILLISECONDS)) {
      throw new PyDebuggerException("Timeout exceeded, failed to load variable: " + debugValue.getName());
    }
    return debugValue.getValue();
  }

  public static ArrayList<PyFrameAccessor.PyAsyncValue<String>> createAsyncValue(PyDebugValue debugValue, Semaphore semaphore) {
    ArrayList<PyFrameAccessor.PyAsyncValue<String>> valuesForEvaluation = new ArrayList<>();
    valuesForEvaluation.add(new PyFrameAccessor.PyAsyncValue<>(debugValue, new PyDebugCallback<>() {
      @Override
      public void ok(String value) {
        debugValue.setValue(value);
        semaphore.release();
      }

      @Override
      public void error(PyDebuggerException exception) {
        semaphore.release();
      }
    }));
    return valuesForEvaluation;
  }

  public static List<PyDebugValue> convertToList(XValueChildrenList childrenList) {
    List<PyDebugValue> values = new ArrayList<>();
    for (int i = 0; i < childrenList.size(); i++) {
      PyDebugValue value = (PyDebugValue)childrenList.getValue(i);
      values.add(value);
    }
    return values;
  }

  @Nullable
  public static PyDebugValue findDebugValueByName(@NotNull List<PyDebugValue> debugValues, @NotNull String name) {
    for (PyDebugValue val : debugValues) {
      if (val.getName().equals(name)) {
        return val;
      }
    }
    return null;
  }

  @Nullable
  public static PydevCompletionVariant findCompletionVariantByName(@NotNull List<PydevCompletionVariant> completions,
                                                                   @NotNull String name) {
    for (PydevCompletionVariant val : completions) {
      if (val.getName().equals(name)) {
        return val;
      }
    }
    return null;
  }

  @NotNull
  protected String output() {
    String consoleNotAvailableMessage = "Console output not available.";
    if (mySession != null && mySession.getConsoleView() != null) {
      PythonDebugLanguageConsoleView pydevConsoleView = (PythonDebugLanguageConsoleView)mySession.getConsoleView();
      ConsoleViewImpl consoleView = pydevConsoleView.getTextConsole();
      return consoleView != null ? XDebuggerTestUtil.getConsoleText(consoleView) : consoleNotAvailableMessage;
    }
    return consoleNotAvailableMessage;
  }

  protected void input(String text) {
    PrintWriter pw = new PrintWriter(myDebugProcess.getProcessHandler().getProcessInput());
    pw.println(text);
    pw.flush();
  }

  protected void outputContains(String substring) {
    Assert.assertTrue(output().contains(substring));
  }

  protected void outputContains(String substring, int times) {
    Assert.assertEquals(times, StringUtil.getOccurrenceCount(output(), substring));
  }

  public void setProcessCanTerminate(boolean processCanTerminate) {
    myProcessCanTerminate = processCanTerminate;
  }

  protected void clearAllBreakpoints() {
    ApplicationManager.getApplication()
                      .invokeLater(() -> XDebuggerTestUtil.removeAllBreakpoints(getProject()), ModalityState.defaultModalityState());
  }

  /**
   * Toggles breakpoint
   *
   * @param file getScriptName() or path to script
   * @param line starting with 0
   */
  protected void toggleBreakpoint(final String file, final int line) {
    doToggleBreakpoint(file, line);
    setBreakpointSuspendPolicy(getProject(), line, myDefaultSuspendPolicy);
  }

  /**
   * Removes breakpoint
   *
   * @param file getScriptName() or path to script
   * @param line starting with 0
   */
  protected void removeBreakpoint(final String file, final int line) {
    ApplicationManager.getApplication().invokeAndWait(() -> XDebuggerTestUtil.removeBreakpoint(getProject(), getFileByPath(file), line),
                                                      ModalityState.defaultModalityState());
  }

  protected void toggleBreakpointInEgg(final String file, final String innerPath, final int line) {
    UIUtil.invokeAndWaitIfNeeded(() -> {
      VirtualFile f = LocalFileSystem.getInstance().findFileByPath(file);
      Assert.assertNotNull(f);
      final VirtualFile jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(f);
      Assert.assertNotNull(jarRoot);
      VirtualFile innerFile = jarRoot.findFileByRelativePath(innerPath);
      Assert.assertNotNull(innerFile);
      XDebuggerTestUtil.toggleBreakpoint(getProject(), innerFile, line);
    });
  }

  public boolean canPutBreakpointAt(Project project, String file, int line) {

    // May be relative or not
    final VirtualFile vFile = getFileByPath(file);
    Assert.assertNotNull(String.format("There is no %s", file), vFile);
    return ReadAction.compute(() -> XDebuggerUtil.getInstance().canPutBreakpointAt(project, vFile, line));
  }

  private void doToggleBreakpoint(String file, int line) {
    Assert.assertTrue(canPutBreakpointAt(getProject(), file, line));
    XDebuggerTestUtil.toggleBreakpoint(getProject(),getFileByPath(file), line);
  }

  public static void setBreakpointSuspendPolicy(Project project, int line, SuspendPolicy policy) {
    XBreakpointManager breakpointManager = XDebuggerManager.getInstance(project).getBreakpointManager();
    for (XBreakpoint breakpoint : XDebuggerTestUtil.getBreakpoints(breakpointManager)) {
      if (breakpoint instanceof XLineBreakpoint lineBreakpoint) {

        if (lineBreakpoint.getLine() == line) {
          WriteAction.runAndWait(() -> lineBreakpoint.setSuspendPolicy(policy));
        }
      }
    }
  }

  public static XBreakpoint addExceptionBreakpoint(IdeaProjectTestFixture fixture, PyExceptionBreakpointProperties properties) {
    return XDebuggerTestUtil.addBreakpoint(fixture.getProject(), PyExceptionBreakpointType.class, properties);
  }

  public static void createExceptionBreak(IdeaProjectTestFixture fixture,
                                          boolean notifyOnTerminate,
                                          boolean notifyOnFirst,
                                          boolean ignoreLibraries,
                                          @Nullable String condition,
                                          @Nullable String logExpression) {
    XDebuggerTestUtil.removeAllBreakpoints(fixture.getProject());
    XDebuggerTestUtil.setDefaultBreakpointEnabled(fixture.getProject(), PyExceptionBreakpointType.class, false);

    PyExceptionBreakpointProperties properties = new PyExceptionBreakpointProperties("BaseException");
    properties.setNotifyOnTerminate(notifyOnTerminate);
    properties.setNotifyOnlyOnFirst(notifyOnFirst);
    properties.setIgnoreLibraries(ignoreLibraries);
    XBreakpoint exceptionBreakpoint = addExceptionBreakpoint(fixture, properties);
    if (condition != null) {
      exceptionBreakpoint.setCondition(condition);
    }
    if (logExpression != null) {
      exceptionBreakpoint.setLogExpression(logExpression);
    }
  }

  public static void createExceptionBreak(IdeaProjectTestFixture fixture,
                                          boolean notifyOnTerminate,
                                          boolean notifyOnFirst,
                                          boolean ignoreLibraries) {
    createExceptionBreak(fixture, notifyOnTerminate, notifyOnFirst, ignoreLibraries, null, null);
  }

  public String getRunningThread() {
    for (PyThreadInfo thread : myDebugProcess.getThreads()) {
      if (!thread.isPydevThread()) {
        if ((thread.getState() == null) || (thread.getState() == PyThreadInfo.State.RUNNING)) {
          return thread.getName();
        }
      }
    }
    return null;
  }

  protected List<String> getNumberOfReferringObjects(String name) throws PyDebuggerException {
    XValue var = XDebuggerTestUtil.evaluate(mySession, name).first;
    final PyReferringObjectsValue value = new PyReferringObjectsValue((PyDebugValue)var);
    EvaluationCallback<List<String>> callback = new EvaluationCallback<>();

    myDebugProcess.loadReferrers(value, new PyDebugCallback<>() {
      @Override
      public void ok(XValueChildrenList valueList) {
        ArrayList<String> values = new ArrayList<>();
        for (int i = 0; i < valueList.size(); ++i) {
          values.add(valueList.getName(i));
        }
        callback.evaluated(values);
      }

      @Override
      public void error(PyDebuggerException exception) {
        callback.errorOccurred(exception.getMessage());
      }
    });

    final Pair<List<String>, String> result = callback.waitFor(NORMAL_TIMEOUT);
    if (result.second != null) {
      throw new PyDebuggerException(result.second);
    }

    return result.first;
  }

  /**
   * Run a command in the debugger console without waiting for the result.
   *
   * @param command to run.
   *
   * @see #consoleExecAndWait(String)
   */
  protected void consoleExec(String command) {
    myDebugProcess.consoleExec(command, new PyDebugCallback<>() {
      @Override
      public void ok(String value) {
      }

      @Override
      public void error(PyDebuggerException exception) {
      }
    });
  }

  /**
   * Run a command in the debugger console and wait until it is executed. It raises the assertion error if the command
   * hasn't finished withing {@link XDebuggerTestUtil#TIMEOUT_MS} milliseconds. It doesn't matter if the command itself
   * has finished successfully or failed (e.g. incorrect commands can be used in tests in purpose).
   *
   * @param command to run.
   */
  protected void consoleExecAndWait(String command) {
    EvaluationCallback<String> callback = new EvaluationCallback<>();
    myDebugProcess.consoleExec(command, new PyDebugCallback<>() {
      @Override
      public void ok(String value) {
        callback.evaluated(value);
      }

      @Override
      public void error(PyDebuggerException exception) {
        callback.errorOccurred(exception.getMessage());
      }
    });
    callback.waitFor(XDebuggerTestUtil.TIMEOUT_MS);
  }

  protected Variable eval(String name) {
    Assert.assertTrue("Eval works only while suspended", mySession.isSuspended());
    XValue var = XDebuggerTestUtil.evaluate(mySession, name).first;
    Assert.assertNotNull("There is no variable named " + name, var);
    return new Variable(var);
  }

  protected void setVal(String name, String value) throws PyDebuggerException {
    XValue var = XDebuggerTestUtil.evaluate(mySession, name).first;
    myDebugProcess.changeVariable((PyDebugValue)var, value);
  }

  /**
   * Waits until the given string appears in the output the given number of times.
   * @param string The string to match output with.
   * @param times The number of times we expect to see the string.
   */
  public void waitForOutput(String string, int times) throws InterruptedException {
    long started = System.currentTimeMillis();
    int matches;

    while ((matches = StringUtil.getOccurrenceCount(output(), string)) != times) {
      if (System.currentTimeMillis() - started > NORMAL_TIMEOUT) {
        Assert.fail("The substring '" + string + "' appeared in the output " + matches + " times, must be " + times + " times.\n" +
                    output());
      }
      Thread.sleep(2000);
    }
  }

  public void waitForOutput(String... string) throws InterruptedException {
    long started = System.currentTimeMillis();

    while (!containsOneOf(output(), string)) {
      if (System.currentTimeMillis() - started > NORMAL_TIMEOUT) {
        Assert.fail("None of '" + StringUtil.join(string, ", ") + "'" + " is not present in output.\n" + output());
      }
      Thread.sleep(2000);
    }
  }

  protected boolean containsOneOf(String output, String[] strings) {
    for (String s : strings) {
      if (output.contains(s)) {
        return true;
      }
    }

    return false;
  }

  public void setShouldPrintOutput(boolean shouldPrintOutput) {
    this.shouldPrintOutput = shouldPrintOutput;
  }

  public String formatStr(int x, int collectionLength) {
    return String.format("%0" + Integer.toString(collectionLength).length() + "d", x);
  }

  public boolean hasChildWithName(XValueChildrenList children, String name) {
    for (int i = 0; i < children.size(); i++)
      // Dictionary key names are followed by the hash so we need to consider only
      // the first word of a name. For lists this operation doesn't have any effect.
      if (children.getName(i).split(" ")[0].equals(name)) return true;
    return false;
  }

  public boolean hasChildWithName(XValueChildrenList children, int name) {
    return hasChildWithName(children, Integer.toString(name));
  }

  public boolean hasChildWithValue(XValueChildrenList children, String value) {
    for (int i = 0; i < children.size(); i++) {
      PyDebugValue current = (PyDebugValue)children.getValue(i);
      if (current.getValue().equals(value)) return true;
    }
    return false;
  }

  public boolean hasChildWithValue(XValueChildrenList children, int value) {
    return hasChildWithValue(children, Integer.toString(value));
  }

  public @NotNull Promise<? extends List<?>> getSmartStepIntoVariantsAsync() {
      XSourcePosition position = XDebuggerManager.getInstance(getProject()).getCurrentSession().getCurrentPosition();
      return myDebugProcess.getSmartStepIntoHandler().computeSmartStepVariantsAsync(position);
  }

  public List<?> getSmartStepIntoVariants() {
    XSourcePosition position = XDebuggerManager.getInstance(getProject()).getCurrentSession().getCurrentPosition();
    return myDebugProcess.getSmartStepIntoHandler().computeSmartStepVariants(position);
  }

  @Override
  public void setUp(final String testName) throws Exception {
    if (myFixture == null) {
      super.setUp(testName);
    }
  }

  @Override
  public void tearDown() throws Exception {
    try {
      EdtTestUtil.runInEdtAndWait(() -> finishSession());
    }
    finally {
      super.tearDown();
    }
  }

  protected void finishSession() {
    disposeDebugProcess();

    if (mySession != null) {
      WriteAction.runAndWait(() -> mySession.stop());

      waitFor(mySession.getDebugProcess().getProcessHandler()); //wait for process termination after session.stop() which is async

      XDebuggerTestUtil.disposeDebugSession(mySession);

      mySession = null;
      myDebugProcess = null;
      myPausedSemaphore = null;
    }


    final ExecutionResult result = myExecutionResult;
    if (myExecutionResult != null) {
      UIUtil.invokeLaterIfNeeded(() -> Disposer.dispose(result.getExecutionConsole()));
      myExecutionResult = null;
    }
  }

  protected abstract void disposeDebugProcess();

  protected void doTest(@Nullable OutputPrinter myOutputPrinter) throws InterruptedException {
    try {
      testing();
      after();
    }
    catch (Throwable e) {
      throw new RuntimeException(output(), e);
    }
    finally {
      doFinally();

      clearAllBreakpoints();

      setProcessCanTerminate(true);

      if (myOutputPrinter != null) {
        myOutputPrinter.stop();
      }

      finishSession();
    }
  }

  protected void debugPaused() {
    if (myPausedSemaphore != null) {
      int availablePermits = myPausedSemaphore.availablePermits();
      if (availablePermits > 0) {
        myLogger.warn("Session was stopped twice in a row. This happens sometimes with debugger, seems it's a race inside Mono");
      }
      else {
        myPausedSemaphore.release();
      }
    }
  }

  protected void debugTerminated(@NotNull ProcessEvent event, @NotNull String out) {
    if (myTerminateSemaphore != null) {
      int availablePermits = myTerminateSemaphore.availablePermits();
      if (availablePermits > 0) {
        myLogger.warn("Session was terminated twice");
      }
      else {
        myTerminateSemaphore.release();
        if (event.getExitCode() != 0 && !myProcessCanTerminate) {
          Assert.fail("Process terminated unexpectedly\n" + out);
        }
      }
    }
  }

  protected void checkSessionPaused() {
    XDebugSession currentSession = XDebuggerManager.getInstance(getProject()).getCurrentSession();
    Assert.assertNotNull(currentSession);
    Assert.assertTrue(currentSession.isSuspended());

    if (myPausedSemaphore.availablePermits() != 0) {
      myLogger.warn("Session was paused twice");
    };
  }

  protected static class EvaluationCallback<T> {
    private final Semaphore myFinished = new Semaphore(0);
    private T myResult;
    private String myErrorMessage;

    public void evaluated(T result) {
      myResult = result;
      myFinished.release();
    }

    public void errorOccurred(@NotNull String errorMessage) {
      myErrorMessage = errorMessage;
      myFinished.release();
    }

    public Pair<T, String> waitFor(long timeoutInMilliseconds) {
      Assert.assertTrue("timed out", XDebuggerTestUtil.waitFor(myFinished, timeoutInMilliseconds));
      return Pair.create(myResult, myErrorMessage);
    }
  }

  public static class Variable {
    private final XTestValueNode myValueNode;

    public Variable(XValue value) {
      myValueNode = XDebuggerTestUtil.computePresentation(value);
    }

    public Variable hasValue(String value) {
      Assert.assertEquals(value, getValue());
      return this;
    }

    public String getValue() {
      return myValueNode.myValue;
    }
  }

  public class OutputPrinter {
    private Thread myThread;

    public void start() {
      myThread = new Thread(() -> doJob(), "py debugger job");
      myThread.setDaemon(true);
      myThread.start();
    }

    private void doJob() {
      int len = 0;
      try {
        while (true) {
          String s = output();
          if (s.length() > len) {
            System.out.print(s.substring(len));
          }
          len = s.length();

          Thread.sleep(500);
        }
      }
      catch (Exception e) {
      }
    }

    public void stop() throws InterruptedException {
      myThread.interrupt();
      myThread.join();
    }
  }
}
