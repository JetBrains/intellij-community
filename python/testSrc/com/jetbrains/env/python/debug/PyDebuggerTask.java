// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.env.python.debug;

import com.google.common.collect.Sets;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.ParamsGroup;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.process.KillableColoredProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Key;
import com.intellij.xdebugger.*;
import com.jetbrains.python.PythonHelper;
import com.jetbrains.python.debugger.PyDebugProcess;
import com.jetbrains.python.debugger.PyDebugRunner;
import com.jetbrains.python.debugger.PyDebugValueExecutionService;
import com.jetbrains.python.run.CommandLinePatcher;
import com.jetbrains.python.run.PythonCommandLineState;
import com.jetbrains.python.run.PythonConfigurationType;
import com.jetbrains.python.run.PythonRunConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.Semaphore;

public class PyDebuggerTask extends PyBaseDebuggerTask {

  private boolean myMultiprocessDebug = false;
  protected PythonRunConfiguration myRunConfiguration;
  private boolean myWaitForTermination = true;


  public PyDebuggerTask(@Nullable final String relativeTestDataPath, String scriptName, String scriptParameters) {
    super(relativeTestDataPath);
    setScriptName(scriptName);
    setScriptParameters(scriptParameters);
    init();
  }

  public PyDebuggerTask(@Nullable final String relativeTestDataPath, String scriptName) {
    this(relativeTestDataPath, scriptName, null);
  }

  protected void init() {

  }

  @Nullable
  @Override
  public Set<String> getTagsToCover() {
    return Sets.newHashSet("python2.7", "python3.5", "python3.6", "python3.7", "python3.8", "jython", "IronPython", "pypy");
  }

  @Override
  public void runTestOn(@NotNull String sdkHome, @Nullable Sdk existingSdk) throws Exception {
    final Project project = getProject();

    final ConfigurationFactory factory = PythonConfigurationType.getInstance().getConfigurationFactories()[0];


    final RunnerAndConfigurationSettings settings =
      RunManager.getInstance(project).createConfiguration("test", factory);

    myRunConfiguration = (PythonRunConfiguration)settings.getConfiguration();

    myRunConfiguration.setSdkHome(sdkHome);
    myRunConfiguration.setScriptName(getScriptName());
    myRunConfiguration.setWorkingDirectory(myFixture.getTempDirPath());
    myRunConfiguration.setScriptParameters(getScriptParameters());

    WriteAction.runAndWait(() -> {
      RunManager runManager = RunManager.getInstance(project);
      runManager.addConfiguration(settings);
      runManager.setSelectedConfiguration(settings);
      Assert.assertSame(settings, runManager.getSelectedConfiguration());
    });

    PyDebugRunner runner = (PyDebugRunner)ProgramRunner.getRunner(getExecutorId(), settings.getConfiguration());
    Assert.assertTrue(runner.canRun(getExecutorId(), myRunConfiguration));

    final Executor executor = DefaultDebugExecutor.getDebugExecutorInstance();
    final ExecutionEnvironment env = new ExecutionEnvironment(executor, runner, settings, project);

    final PythonCommandLineState pyState = (PythonCommandLineState)myRunConfiguration.getState(executor, env);

    assert pyState != null;
    pyState.setMultiprocessDebug(isMultiprocessDebug());

    final ServerSocket serverSocket;
    try {
      //noinspection SocketOpenedButNotSafelyClosed
      serverSocket = new ServerSocket(0);
    }
    catch (IOException e) {
      throw new ExecutionException("Failed to find free socket port", e);
    }


    final int serverLocalPort = serverSocket.getLocalPort();
    final RunProfile profile = env.getRunProfile();

    createExceptionBreak(myFixture, false, false, false); //turn off exception breakpoints by default

    before();

    setProcessCanTerminate(false);

    myTerminateSemaphore = new Semaphore(0);

    WriteAction.computeAndWait(() -> {
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


            StringBuilder output = new StringBuilder();

            myDebugProcess.getProcessHandler().addProcessListener(new ProcessAdapter() {

              @Override
              public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
                output.append(event.getText());
              }

              @Override
              public void processTerminated(@NotNull ProcessEvent event) {
                myTerminateSemaphore.release();
                if (event.getExitCode() != 0 && !myProcessCanTerminate) {
                  Assert.fail("Process terminated unexpectedly\n" + output.toString());
                }
              }
            });


            myDebugProcess.getProcessHandler().startNotify();

            return myDebugProcess;
          }
        });
      return myExecutionResult;
    });

    OutputPrinter myOutputPrinter = null;
    if (shouldPrintOutput) {
      myOutputPrinter = new OutputPrinter();
      myOutputPrinter.start();
    }


    myPausedSemaphore = new Semaphore(0);


    mySession.addSessionListener(new XDebugSessionListener() {
      @Override
      public void sessionPaused() {
        if (myPausedSemaphore != null) {
          myPausedSemaphore.release();
        }
      }
    });

    doTest(myOutputPrinter);
  }

  protected boolean usePytestRunner() {
    return false;
  }

  protected CommandLinePatcher[] createCommandLinePatchers(PyDebugRunner runner, PythonCommandLineState pyState, RunProfile profile,
                                                           int serverLocalPort) {
    final CommandLinePatcher[] debugPatchers = runner.createCommandLinePatchers(myFixture.getProject(), pyState, profile, serverLocalPort);
    if (!usePytestRunner()) {
      return debugPatchers;
    }
    ArrayList<CommandLinePatcher> result = new ArrayList<>();
    result.add(pytestPatcher());
    result.addAll(Arrays.asList(debugPatchers));
    return result.toArray(new CommandLinePatcher[0]);
  }

  private static CommandLinePatcher pytestPatcher() {
    return new CommandLinePatcher() {
      @Override
      public void patchCommandLine(GeneralCommandLine commandLine) {
        final ParamsGroup scriptGroup = commandLine.getParametersList().getParamsGroup(PythonCommandLineState.GROUP_SCRIPT);
        scriptGroup.addParameterAt(0, "--path");
        scriptGroup.addParameterAt(0, PythonHelper.PYTEST.asParamString());

        commandLine.getEnvironment().put("PYTEST_RUN_CONFIG", "True");
      }
    };
  }

  protected String getExecutorId() {
    return DefaultDebugExecutor.EXECUTOR_ID;
  }

  public PythonRunConfiguration getRunConfiguration() {
    return myRunConfiguration;
  }

  private boolean isMultiprocessDebug() {
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
    if (myDebugProcess.getProcessHandler() instanceof KillableColoredProcessHandler) {
      KillableColoredProcessHandler h = (KillableColoredProcessHandler)myDebugProcess.getProcessHandler();

      h.killProcess();
    }
    else {
      myDebugProcess.getProcessHandler().destroyProcess();
    }
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
    for(int line : lines)
      toggleBreakpoint(file, line);
  }
}
