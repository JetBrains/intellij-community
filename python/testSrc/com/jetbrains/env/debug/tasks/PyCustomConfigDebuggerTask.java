// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.env.debug.tasks;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.process.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.target.TargetEnvironment;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.testFramework.common.ThreadLeakTracker;
import com.intellij.xdebugger.*;
import com.jetbrains.env.LockSafeSemaphore;
import com.jetbrains.python.debugger.PyDebugProcess;
import com.jetbrains.python.debugger.PyDebugRunner;
import com.jetbrains.python.debugger.PyDebugValueExecutionService;
import com.jetbrains.python.debugger.PyDebuggerOptionsProvider;
import com.jetbrains.python.run.AbstractPythonRunConfiguration;
import com.jetbrains.python.run.CommandLinePatcher;
import com.jetbrains.python.run.PythonCommandLineState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;

import java.io.IOException;
import java.net.ServerSocket;
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
  private boolean myMultiprocessDebug = false;
  private boolean myWaitForTermination = true;
  private final @NotNull StringBuilder myOutputBuilder = new StringBuilder();
  private final @NotNull StringBuilder myStdErrBuilder = new StringBuilder();
  private Disposable myThreadLeakDisposable;

  protected PyCustomConfigDebuggerTask(@Nullable String relativeTestDataPath) {
    super(relativeTestDataPath);
    setProcessCanTerminate(false);
  }

  protected abstract AbstractPythonRunConfiguration<? extends AbstractPythonRunConfiguration<?>> createRunConfiguration(
    @NotNull String sdkHome, @Nullable Sdk existingSdk);

  protected abstract CommandLinePatcher[] createCommandLinePatchers(PyDebugRunner runner, PythonCommandLineState pyState,
                                                                    RunProfile profile, int serverLocalPort);

  @Override
  public void runTestOn(@NotNull String sdkHome, @Nullable Sdk existingSdk) throws Exception {
    myThreadLeakDisposable = Disposer.newDisposable();
    ThreadLeakTracker.longRunningThreadCreated(myThreadLeakDisposable, "");

    if (Registry.is("python.debug.use.single.port")) {
      runTestInClientMode(sdkHome, existingSdk);
    }
    else {
      runTestInServerMode(sdkHome, existingSdk);
    }
  }

  private void runTestInServerMode(@NotNull String sdkHome, @Nullable Sdk existingSdk) throws Exception {
    Project project = getProject();

    myRunConfiguration = createRunConfiguration(sdkHome, existingSdk);

    WriteAction.runAndWait(() -> {
      RunManager runManager = RunManager.getInstance(project);
      runManager.addConfiguration(mySettings);
      runManager.setSelectedConfiguration(mySettings);
      Assert.assertSame(mySettings, runManager.getSelectedConfiguration());
    });

    PyDebugRunner runner = (PyDebugRunner)ProgramRunner.getRunner(getExecutorId(), mySettings.getConfiguration());
    Assert.assertTrue(runner.canRun(getExecutorId(), myRunConfiguration));

    Executor executor = DefaultDebugExecutor.getDebugExecutorInstance();
    ExecutionEnvironment env = new ExecutionEnvironment(executor, runner, mySettings, project);

    PythonCommandLineState pyState = (PythonCommandLineState)myRunConfiguration.getState(executor, env);

    assert pyState != null;
    pyState.setMultiprocessDebug(isMultiprocessDebug());

    try (ServerSocket serverSocket = new ServerSocket(0)) {
      int serverLocalPort = serverSocket.getLocalPort();
      RunProfile profile = env.getRunProfile();

      createExceptionBreak(myFixture, false, false, false); //turn off exception breakpoints by default

      before();

      myTerminateSemaphore = new Semaphore(0);

      WriteAction.runAndWait(() -> {
        myExecutionResult =
          pyState.execute(executor, createCommandLinePatchers(runner, pyState, profile, serverLocalPort));

        mySession = XDebuggerManager.getInstance(getProject()).
          startSession(env, new XDebugProcessStarter() {
            @Override
            @NotNull
            public XDebugProcess start(@NotNull final XDebugSession session) {
              myDebugProcess =
                new PyDebugProcess(session, serverSocket, myExecutionResult.getExecutionConsole(), myExecutionResult.getProcessHandler(),
                                   isMultiprocessDebug());
              myDebugProcess.getProcessHandler().addProcessListener(new ProcessListener() {
                @Override
                public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
                  myOutputBuilder.append(event.getText());
                  if (outputType == ProcessOutputType.STDERR) {
                    myStdErrBuilder.append(event.getText());
                  }
                }

                @Override
                public void processTerminated(@NotNull ProcessEvent event) {
                  myTerminateSemaphore.release();
                  if (event.getExitCode() != 0 && !myProcessCanTerminate) {
                    Assert.fail("Process terminated unexpectedly\n" + myOutputBuilder);
                  }
                }
              });

              myDebugProcess.getProcessHandler().startNotify();
              return myDebugProcess;
            }
          });
      });

      myPausedSemaphore = new LockSafeSemaphore(0);

      mySession.addSessionListener(new XDebugSessionListener() {
        @Override
        public void sessionPaused() {
          if (myPausedSemaphore != null) {
            myPausedSemaphore.release();
          }
        }
      });

      doTest(null);
    }
    catch (IOException e) {
      throw new ExecutionException("Failed to find free socket port", e); // NON-NLS
    }
  }

  private void runTestInClientMode(@NotNull String sdkHome, @Nullable Sdk existingSdk) throws Exception {
    Project project = getProject();

    myRunConfiguration = createRunConfiguration(sdkHome, existingSdk);

    WriteAction.runAndWait(() -> {
      RunManager runManager = RunManager.getInstance(project);
      runManager.addConfiguration(mySettings);
      runManager.setSelectedConfiguration(mySettings);
      Assert.assertSame(mySettings, runManager.getSelectedConfiguration());
    });

    PyDebugRunner runner = (PyDebugRunner)ProgramRunner.getRunner(getExecutorId(), mySettings.getConfiguration());
    Assert.assertTrue(runner.canRun(getExecutorId(), myRunConfiguration));

    Executor executor = DefaultDebugExecutor.getDebugExecutorInstance();
    ExecutionEnvironment env = new ExecutionEnvironment(executor, runner, mySettings, project);

    PythonCommandLineState pyState = (PythonCommandLineState)myRunConfiguration.getState(executor, env);

    assert pyState != null;
    pyState.setMultiprocessDebug(isMultiprocessDebug());

    RunProfile profile = env.getRunProfile();

    createExceptionBreak(myFixture, false, false, false); //turn off exception breakpoints by default

    before();

    myTerminateSemaphore = new Semaphore(0);

    WriteAction.runAndWait(() -> {
      var port = PyDebuggerOptionsProvider.getInstance(project).getDebuggerPort();

      TargetEnvironment.TargetPortBinding targetPortBinding =
        new TargetEnvironment.TargetPortBinding(port, port);

      var builder = runner.new PythonDebuggerServerModeTargetedCommandLineBuilder(project, pyState, profile, targetPortBinding);

      myExecutionResult =
        pyState.execute(executor, builder);

      mySession = XDebuggerManager.getInstance(getProject()).
        startSession(env, new XDebugProcessStarter() {
          @Override
          @NotNull
          public XDebugProcess start(@NotNull final XDebugSession session) {
            myDebugProcess =
              new PyDebugProcess(session, myExecutionResult.getExecutionConsole(), myExecutionResult.getProcessHandler(),
                                 "localhost", port);
            myDebugProcess.getProcessHandler().addProcessListener(new ProcessListener() {
              @Override
              public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
                myOutputBuilder.append(event.getText());
                if (outputType == ProcessOutputType.STDERR) {
                  myStdErrBuilder.append(event.getText());
                }
              }

              @Override
              public void processTerminated(@NotNull ProcessEvent event) {
                myTerminateSemaphore.release();
                if (event.getExitCode() != 0 && !myProcessCanTerminate) {
                  Assert.fail("Process terminated unexpectedly\n" + myOutputBuilder);
                }
              }
            });

            myDebugProcess.getProcessHandler().startNotify();
            return myDebugProcess;
          }
        });
    });

    myPausedSemaphore = new LockSafeSemaphore(0);

    mySession.addSessionListener(new XDebugSessionListener() {
      @Override
      public void sessionPaused() {
        if (myPausedSemaphore != null) {
          myPausedSemaphore.release();
        }
      }
    });

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

  public boolean isMultiprocessDebug() {
    return myMultiprocessDebug;
  }

  public void setMultiprocessDebug(boolean multiprocessDebug) {
    myMultiprocessDebug = multiprocessDebug;
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
}
