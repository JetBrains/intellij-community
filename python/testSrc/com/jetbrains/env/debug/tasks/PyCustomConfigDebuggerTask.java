// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.env.debug.tasks;

import com.intellij.execution.*;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.process.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.testFramework.common.ThreadLeakTracker;
import com.intellij.xdebugger.*;
import com.jetbrains.python.debugger.PyDebugRunner;
import com.jetbrains.python.debugger.PyDebugValueExecutionService;
import com.jetbrains.python.run.AbstractPythonRunConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;

import java.util.concurrent.Semaphore;

/**
 * A base class for Python debugger test tasks. The main purpose is to make it possible to execute different run configurations
 * with debug executor. The type of the run configuration is controlled by redefining the {@link #createRunConfiguration} method.
 *
 * @see PyDebuggerTask
 * @see PyUnitTestDebuggingTask
 */
public abstract class PyCustomConfigDebuggerTask extends PyBaseDebuggerTask {

  protected AbstractPythonRunConfiguration<? extends AbstractPythonRunConfiguration<?>> myRunConfiguration;
  protected RunnerAndConfigurationSettings mySettings;
  private boolean myWaitForTermination = true;
  private final @NotNull StringBuilder myOutputBuilder = new StringBuilder();
  private final @NotNull StringBuilder myStdErrBuilder = new StringBuilder();
  private Disposable myThreadLeakDisposable;
  private PyDebugRunner myRunner = null;

  protected PyCustomConfigDebuggerTask(@Nullable String relativeTestDataPath) {
    super(relativeTestDataPath);
    setProcessCanTerminate(false);
  }

  protected abstract AbstractPythonRunConfiguration<? extends AbstractPythonRunConfiguration<?>> createRunConfiguration(
    @NotNull String sdkHome, @Nullable Sdk existingSdk);

  @Override
  public void runTestOn(@NotNull String sdkHome, @Nullable Sdk existingSdk) throws Exception {
    myThreadLeakDisposable = Disposer.newDisposable();
    ThreadLeakTracker.longRunningThreadCreated(myThreadLeakDisposable, "");
    myPausedSemaphore = new Semaphore(0);
    myTerminateSemaphore = new Semaphore(0);
    runTestAsProd(sdkHome, existingSdk);
  }

  private void runTestAsProd(@NotNull String sdkHome, @Nullable Sdk existingSdk) throws Exception {
    Project project = getProject();
    createConfiguration(sdkHome, existingSdk);
    myRunner = getRunner();
    myRunner.resetProcess();
    Executor executor = DefaultDebugExecutor.getDebugExecutorInstance();
    ExecutionEnvironment env = new ExecutionEnvironment(executor, myRunner, mySettings, project);
    createExceptionBreak(myFixture, false, false, false); //turn off exception breakpoints by default
    before();

    WriteAction.runAndWait(() -> {
        try {
          myRunner.executeWithListener(env, createSessionListener());
        }
        catch (ExecutionException e) {
          throw new RuntimeException(e);
        }
    });

    // Waiting PythonDebugProcess initialization
    waitingDebugProcess();

    myDebugProcess = myRunner.getProcess();
    mySession = myDebugProcess.getSession();
    myDebugProcess.getProcessHandler().addProcessListener(createProcessListener());

    doTest(null);
  }

  @Override
  protected void disposeDebugProcess() {
    if (myDebugProcess != null) {
      ProcessHandler processHandler = myDebugProcess.getProcessHandler();

      myDebugProcess.stop();

      if (myWaitForTermination) {
        // for some tests (with infinite loops, for example, it has no sense)
        waitFor(processHandler);
      }

      myRunner.resetProcess();

      try {
        PyDebugValueExecutionService.getInstance(getProject()).shutDownNow(NORMAL_TIMEOUT);
      }
      catch (InterruptedException e) {
        //pass
      }

      if (!processHandler.isProcessTerminated()) {
        killDebugProcess();
        if (!waitFor(processHandler)) {
          new Throwable("Cannot stop debugger process").printStackTrace();
        }
      }
    }
  }

  protected void killDebugProcess() {
    if (myDebugProcess.getProcessHandler() instanceof KillableColoredProcessHandler h) {

      h.killProcess();
    }
    else {
      myDebugProcess.getProcessHandler().destroyProcess();
    }
  }

  @Override
  protected @NotNull String output() {
    return myOutputBuilder.toString();
  }

  protected @NotNull String stderr() {
    return myStdErrBuilder.toString();
  }

  protected String getExecutorId() {
    return DefaultDebugExecutor.EXECUTOR_ID;
  }

  public void setWaitForTermination(boolean waitForTermination) {
    myWaitForTermination = waitForTermination;
  }

  protected void waitForAllThreadsPause() throws InterruptedException {
    waitForPause();
    Assert.assertTrue(String.format("All threads didn't stop within timeout\n" +
                                    "Output: %s", output()), waitForAllThreads());
    XDebuggerTestUtil.waitForSwing();
  }

  protected boolean waitForAllThreads() throws InterruptedException {
    long until = System.currentTimeMillis() + NORMAL_TIMEOUT;
    while (System.currentTimeMillis() < until && getRunningThread() != null) {
      Thread.sleep(1000);
    }
    return getRunningThread() == null;
  }

  /**
   * Toggles breakpoint in the script returned by {@link PyDebuggerTask#getScriptName()}.
   *
   * @param line starting with 0
   */
  protected void toggleBreakpoint(int line) {
    toggleBreakpoint(getFilePath(getScriptName()), line);
  }

  /**
   * Toggles multiple breakpoints with {@link PyDebuggerTask#toggleBreakpoint(int)}.
   */
  protected void toggleBreakpoints(int... lines) {
    toggleBreakpoints(getFilePath(getScriptName()), lines);
  }

  /**
   * Toggles multiple breakpoints with {@link PyDebuggerTask#toggleBreakpoint(String, int)}.
   */
  protected void toggleBreakpoints(@NotNull String file, int... lines) {
    for (int line : lines) {
      toggleBreakpoint(file, line);
    }
  }

  @Override
  public void tearDown() throws Exception {
    try {
      super.tearDown();
    } finally {
      if (myThreadLeakDisposable != null) {
        Disposer.dispose(myThreadLeakDisposable);
      }
    }
  }

  private XDebugSessionListener createSessionListener() {
    return new XDebugSessionListener() {
      @Override
      public void sessionPaused() {
        debugPaused();
      }
    };
  }

  private ProcessListener createProcessListener() {
    return new ProcessListener() {
      @Override
      public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
        myOutputBuilder.append(event.getText());
        if (outputType == ProcessOutputType.STDERR) {
          myStdErrBuilder.append(event.getText());
        }
      }

      @Override
      public void processTerminated(@NotNull ProcessEvent event) {
        debugTerminated(event, myOutputBuilder.toString());
      }
    };
  }

  private void createConfiguration(@NotNull String sdkHome, @Nullable Sdk existingSdk) {
    myRunConfiguration = createRunConfiguration(sdkHome, existingSdk);

    WriteAction.runAndWait(() -> {
      RunManager runManager = RunManager.getInstance(getProject());
      runManager.addConfiguration(mySettings);
      runManager.setSelectedConfiguration(mySettings);
      Assert.assertSame(mySettings, runManager.getSelectedConfiguration());
    });
  }

  private PyDebugRunner getRunner() {
    PyDebugRunner runner = (PyDebugRunner)ProgramRunner.getRunner(getExecutorId(), mySettings.getConfiguration());
    Assert.assertTrue(runner.canRun(getExecutorId(), myRunConfiguration));
    return runner;
  }

  private void waitingDebugProcess() throws InterruptedException {
    long timeout = 10000;
    long startTime = System.currentTimeMillis();
    while (myRunner.getProcess() == null) {
      if (System.currentTimeMillis() - startTime > timeout) {
        throw new RuntimeException("Process didn't start within timeout");
      }
      Thread.sleep(500);
    }
  }
}
