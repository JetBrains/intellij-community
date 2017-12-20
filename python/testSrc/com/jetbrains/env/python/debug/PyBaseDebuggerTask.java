/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.env.python.debug;

import com.google.common.collect.Sets;
import com.intellij.execution.ExecutionResult;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.EdtTestUtil;
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
import com.jetbrains.python.debugger.*;
import com.jetbrains.python.debugger.pydev.PyDebugCallback;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.junit.Assert;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Semaphore;

/**
 * @author traff
 */
public abstract class PyBaseDebuggerTask extends PyExecutionFixtureTestTask {
  private Set<Pair<String, Integer>> myBreakpoints = Sets.newHashSet();
  protected PyDebugProcess myDebugProcess;
  protected XDebugSession mySession;
  protected Semaphore myPausedSemaphore;
  protected Semaphore myTerminateSemaphore;
  protected boolean shouldPrintOutput = false;
  protected boolean myProcessCanTerminate;
  protected ExecutionResult myExecutionResult;
  protected SuspendPolicy myDefaultSuspendPolicy = SuspendPolicy.THREAD;

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
    XDebugSession currentSession = XDebuggerManager.getInstance(getProject()).getCurrentSession();

    Assert.assertTrue(currentSession.isSuspended());
    Assert.assertEquals(0, myPausedSemaphore.availablePermits());

    currentSession.resume();
  }

  protected void stepOver() {
    XDebugSession currentSession = XDebuggerManager.getInstance(getProject()).getCurrentSession();

    Assert.assertTrue(currentSession.isSuspended());
    Assert.assertEquals(0, myPausedSemaphore.availablePermits());

    currentSession.stepOver(false);
  }

  protected void stepInto() {
    XDebugSession currentSession = XDebuggerManager.getInstance(getProject()).getCurrentSession();

    Assert.assertTrue(currentSession.isSuspended());
    Assert.assertEquals(0, myPausedSemaphore.availablePermits());

    currentSession.stepInto();
  }

  @TestOnly
  protected void stepIntoMyCode() {
    XDebugSession currentSession = XDebuggerManager.getInstance(getProject()).getCurrentSession();

    Assert.assertTrue(currentSession.isSuspended());
    Assert.assertEquals(0, myPausedSemaphore.availablePermits());

    PyDebugProcess debugProcess = (PyDebugProcess)currentSession.getDebugProcess();
    debugProcess.startStepIntoMyCode(currentSession.getSuspendContext());
  }

  protected void smartStepInto(String funcName) {
    XDebugSession currentSession = XDebuggerManager.getInstance(getProject()).getCurrentSession();

    Assert.assertTrue(currentSession.isSuspended());
    Assert.assertEquals(0, myPausedSemaphore.availablePermits());

    myDebugProcess.startSmartStepInto(funcName);
  }

  protected Pair<Boolean, String> setNextStatement(int line) throws PyDebuggerException {
    XDebugSession currentSession = XDebuggerManager.getInstance(getProject()).getCurrentSession();
    XSourcePosition position = currentSession.getCurrentPosition();
    EvaluationCallback<Pair<Boolean, String>> callback = new EvaluationCallback<>();

    myDebugProcess.startSetNextStatement(
      currentSession.getSuspendContext(),
      XDebuggerUtil.getInstance().createPosition(position.getFile(), line),
      new PyDebugCallback<Pair<Boolean, String>>() {
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

  protected List<PyDebugValue> loadFrame() throws PyDebuggerException {
    return convertToList(myDebugProcess.loadFrame());
  }

  protected String computeValueAsync(List<PyDebugValue> debugValues, String name) throws PyDebuggerException {
    final PyDebugValue debugValue = findDebugValueByName(debugValues, name);
    assert debugValue != null;
    Semaphore variableSemaphore = new Semaphore(0);
    ArrayList<PyFrameAccessor.PyAsyncValue<String>> valuesForEvaluation = new ArrayList<>();
    valuesForEvaluation.add(new PyFrameAccessor.PyAsyncValue<>(debugValue, new PyDebugCallback<String>() {
      @Override
      public void ok(String value) {
        debugValue.setValue(value);
        variableSemaphore.release();
      }

      @Override
      public void error(PyDebuggerException exception) {
        variableSemaphore.release();
      }
    }));
    myDebugProcess.loadAsyncVariablesValues(valuesForEvaluation);
    XDebuggerTestUtil.waitFor(variableSemaphore, NORMAL_TIMEOUT);
    return debugValue.getValue();
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

  @NotNull
  protected String output() {
    if (mySession != null && mySession.getConsoleView() != null) {
      PythonDebugLanguageConsoleView pydevConsoleView = (PythonDebugLanguageConsoleView)mySession.getConsoleView();
      return XDebuggerTestUtil.getConsoleText(pydevConsoleView.getTextConsole());
    }
    return "Console output not available.";
  }

  protected void input(String text) {
    PrintWriter pw = new PrintWriter(myDebugProcess.getProcessHandler().getProcessInput());
    pw.println(text);
    pw.flush();
  }

  private void outputContains(String substring) {
    Assert.assertTrue(output().contains(substring));
  }

  public void setProcessCanTerminate(boolean processCanTerminate) {
    myProcessCanTerminate = processCanTerminate;
  }

  protected void clearAllBreakpoints() {

    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> XDebuggerTestUtil.removeAllBreakpoints(getProject()));
  }

  /**
   * Toggles breakpoint
   *
   * @param file getScriptName() or path to script
   * @param line starting with 0
   */
  protected void toggleBreakpoint(final String file, final int line) {
    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> doToggleBreakpoint(file, line));
    setBreakpointSuspendPolicy(getProject(), line, myDefaultSuspendPolicy);

    addOrRemoveBreakpoint(file, line);
  }

  private void addOrRemoveBreakpoint(String file, int line) {
    if (myBreakpoints.contains(Pair.create(file, line))) {
      myBreakpoints.remove(Pair.create(file, line));
    }
    else {
      myBreakpoints.add(Pair.create(file, line));
    }
  }

  protected void toggleBreakpointInEgg(final String file, final String innerPath, final int line) {
    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
      VirtualFile f = LocalFileSystem.getInstance().findFileByPath(file);
      Assert.assertNotNull(f);
      final VirtualFile jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(f);
      Assert.assertNotNull(jarRoot);
      VirtualFile innerFile = jarRoot.findFileByRelativePath(innerPath);
      Assert.assertNotNull(innerFile);
      XDebuggerTestUtil.toggleBreakpoint(getProject(), innerFile, line);
    });

    addOrRemoveBreakpoint(file, line);
  }

  public boolean canPutBreakpointAt(Project project, String file, int line) {

    // May be relative or not
    final VirtualFile vFile = getFileByPath(file);
    Assert.assertNotNull(String.format("There is no %s", file), vFile);
    return XDebuggerUtil.getInstance().canPutBreakpointAt(project, vFile, line);
  }

  private void doToggleBreakpoint(String file, int line) {
    Assert.assertTrue(canPutBreakpointAt(getProject(), file, line));
    XDebuggerTestUtil.toggleBreakpoint(getProject(),getFileByPath(file), line);
  }

  public static void setBreakpointSuspendPolicy(Project project, int line, SuspendPolicy policy) {
    XBreakpointManager breakpointManager = XDebuggerManager.getInstance(project).getBreakpointManager();
    for (XBreakpoint breakpoint : XDebuggerTestUtil.getBreakpoints(breakpointManager)) {
      if (breakpoint instanceof XLineBreakpoint) {
        final XLineBreakpoint lineBreakpoint = (XLineBreakpoint)breakpoint;

        if (lineBreakpoint.getLine() == line) {
          new WriteAction() {
            @Override
            protected void run(@NotNull Result result) {
              lineBreakpoint.setSuspendPolicy(policy);
            }
          }.execute();
        }
      }
    }
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

    myDebugProcess.loadReferrers(value, new PyDebugCallback<XValueChildrenList>() {
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

  protected void consoleExec(String command) throws PyDebuggerException {
    // We can't wait for result with a callback, because console just prints it to output
    myDebugProcess.consoleExec(command, new PyDebugCallback<String>() {
      @Override
      public void ok(String value) {
      }

      @Override
      public void error(PyDebuggerException exception) {
      }
    });
  }

  protected Variable eval(String name) throws InterruptedException {
    Assert.assertTrue("Eval works only while suspended", mySession.isSuspended());
    XValue var = XDebuggerTestUtil.evaluate(mySession, name).first;
    Assert.assertNotNull("There is no variable named " + name, var);
    return new Variable(var);
  }

  protected void setVal(String name, String value) throws PyDebuggerException {
    XValue var = XDebuggerTestUtil.evaluate(mySession, name).first;
    myDebugProcess.changeVariable((PyDebugValue)var, value);
  }

  public void waitForOutput(String... string) throws InterruptedException {
    long started = System.currentTimeMillis();

    while (!containsOneOf(output(), string)) {
      if (System.currentTimeMillis() - started > myTimeout) {
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

  @Override
  public void setUp(final String testName) throws Exception {
    if (myFixture == null) {
      super.setUp(testName);
    }
  }

  @Override
  public void tearDown() throws Exception {
    try {
      EdtTestUtil.runInEdtAndWait(() ->finishSession());
    }finally {
      PyBaseDebuggerTask.super.tearDown();
    }
  }

  protected void finishSession() {
    disposeDebugProcess();

    if (mySession != null) {
      new WriteAction() {
        protected void run(@NotNull Result result) {
          mySession.stop();
        }
      }.execute();

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

  protected static class Variable {
    private final XTestValueNode myValueNode;

    public Variable(XValue value) {
      myValueNode = XDebuggerTestUtil.computePresentation(value);
    }

    public Variable hasValue(String value) {
      Assert.assertEquals(value, myValueNode.myValue);
      return this;
    }

    public Variable hasType(String type) {
      Assert.assertEquals(type, myValueNode.myType);
      return this;
    }

    public Variable hasName(String name) {
      Assert.assertEquals(name, myValueNode.myName);
      return this;
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
