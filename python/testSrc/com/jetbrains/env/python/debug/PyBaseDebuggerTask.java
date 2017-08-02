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
import java.lang.reflect.InvocationTargetException;
import java.util.Set;
import java.util.concurrent.Semaphore;

/**
 * @author traff
 */
public abstract class PyBaseDebuggerTask extends PyExecutionFixtureTestTask {
  private Set<Pair<String, Integer>> myBreakpoints = Sets.newHashSet();
  protected XDebugProcess myDebugProcess;
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

  protected void waitForPause() throws InterruptedException, InvocationTargetException {
    Assert.assertTrue("Debugger didn't stopped within timeout\nOutput:" + output(), waitFor(myPausedSemaphore));

    XDebuggerTestUtil.waitForSwing();
  }

  abstract PyDebugProcess getPyDebugProcess();


  protected void waitForTerminate() throws InterruptedException, InvocationTargetException {
    setProcessCanTerminate(true);

    Assert.assertTrue("Debugger didn't terminated within timeout\nOutput:" + output(), waitFor(myTerminateSemaphore));
    XDebuggerTestUtil.waitForSwing();
  }

  protected void runToLine(int line) throws InvocationTargetException, InterruptedException {
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

    getPyDebugProcess().startSmartStepInto(funcName);
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
            protected void run(@NotNull Result result) throws Throwable {
              lineBreakpoint.setSuspendPolicy(policy);
            }
          }.execute();
        }
      }
    }
  }

  public String getRunningThread() {
    for (PyThreadInfo thread : getPyDebugProcess().getThreads()) {
      if (!thread.isPydevThread()) {
        if ((thread.getState() == null) || (thread.getState() == PyThreadInfo.State.RUNNING)) {
          return thread.getName();
        }
      }
    }
    return null;
  }

  protected int getNumberOfReferringObjects(String name) throws PyDebuggerException {
    XValue var = XDebuggerTestUtil.evaluate(mySession, name).first;
    final PyReferringObjectsValue value = new PyReferringObjectsValue((PyDebugValue)var);
    EvaluationCallback callback = new EvaluationCallback();

    getPyDebugProcess().loadReferrers(value, new PyDebugCallback<XValueChildrenList>() {
      @Override
      public void ok(XValueChildrenList valueList) {
        callback.evaluated(valueList.size());
      }

      @Override
      public void error(PyDebuggerException exception) {
        callback.errorOccurred(exception.getMessage());
      }
    });

    final Pair<Integer, String> result = callback.waitFor(NORMAL_TIMEOUT);
    if (result.second != null) {
      throw new PyDebuggerException(result.second);
    }

    return result.first;
  }

  protected Variable eval(String name) throws InterruptedException {
    Assert.assertTrue("Eval works only while suspended", mySession.isSuspended());
    XValue var = XDebuggerTestUtil.evaluate(mySession, name).first;
    Assert.assertNotNull("There is no variable named " + name, var);
    return new Variable(var);
  }

  protected void setVal(String name, String value) throws InterruptedException, PyDebuggerException {
    XValue var = XDebuggerTestUtil.evaluate(mySession, name).first;
    getPyDebugProcess().changeVariable((PyDebugValue)var, value);
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
    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
      try {
        finishSession();

        PyBaseDebuggerTask.super.tearDown();
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    });
  }

  protected void finishSession() throws InterruptedException {
    disposeDebugProcess();

    if (mySession != null) {
      new WriteAction() {
        protected void run(@NotNull Result result) throws Throwable {
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

  protected abstract void disposeDebugProcess() throws InterruptedException;

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

  protected static class EvaluationCallback {
    private final Semaphore myFinished = new Semaphore(0);
    private int myResult;
    private String myErrorMessage;

    public void evaluated(int result) {
      myResult = result;
      myFinished.release();
    }

    public void errorOccurred(@NotNull String errorMessage) {
      myErrorMessage = errorMessage;
      myFinished.release();
    }

    public Pair<Integer, String> waitFor(long timeoutInMilliseconds) {
      Assert.assertTrue("timed out", XDebuggerTestUtil.waitFor(myFinished, timeoutInMilliseconds));
      return Pair.create(myResult, myErrorMessage);
    }
  }

  protected static class Variable {
    private final XTestValueNode myValueNode;

    public Variable(XValue value) throws InterruptedException {
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
