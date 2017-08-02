/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.execution.*;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.process.KillableColoredProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.xdebugger.*;
import com.jetbrains.env.python.PythonDebuggerTest;
import com.jetbrains.python.debugger.PyDebugProcess;
import com.jetbrains.python.debugger.PyDebugRunner;
import com.jetbrains.python.run.CommandLinePatcher;
import com.jetbrains.python.run.PythonCommandLineState;
import com.jetbrains.python.run.PythonConfigurationType;
import com.jetbrains.python.run.PythonRunConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.ServerSocket;
import java.util.Set;
import java.util.concurrent.Semaphore;

/**
 * @author traff
 */
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
    return Sets.newHashSet("python2.6", "python2.7", "python3.5", "python3.6", "jython", "IronPython", "pypy");
  }

  public void runTestOn(String sdkHome) throws Exception {
    final Project project = getProject();

    final ConfigurationFactory factory = PythonConfigurationType.getInstance().getConfigurationFactories()[0];


    final RunnerAndConfigurationSettings settings =
      RunManager.getInstance(project).createRunConfiguration("test", factory);

    myRunConfiguration = (PythonRunConfiguration)settings.getConfiguration();

    myRunConfiguration.setSdkHome(sdkHome);
    myRunConfiguration.setScriptName(getScriptName());
    myRunConfiguration.setWorkingDirectory(myFixture.getTempDirPath());
    myRunConfiguration.setScriptParameters(getScriptParameters());

    new WriteAction() {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        RunManager runManager = RunManager.getInstance(project);
        runManager.addConfiguration(settings, false);
        runManager.setSelectedConfiguration(settings);
        Assert.assertSame(settings, runManager.getSelectedConfiguration());
      }
    }.execute();

    final PyDebugRunner runner = (PyDebugRunner)ProgramRunnerUtil.getRunner(getExecutorId(), settings);
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

    PythonDebuggerTest.createExceptionBreak(myFixture, false, false, false); //turn off exception breakpoints by default

    before();

    setProcessCanTerminate(false);

    myTerminateSemaphore = new Semaphore(0);

    CommandLinePatcher[] patchers = runner.createCommandLinePatchers(myFixture.getProject(), pyState, profile, serverLocalPort);

    getSessionInstallerAction(executor, env, pyState, serverSocket,
                              patchers).execute().getResultObject();

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

  @NotNull
  protected WriteAction<ExecutionResult> getSessionInstallerAction(Executor executor,
                                                                   ExecutionEnvironment env,
                                                                   PythonCommandLineState pyState,
                                                                   ServerSocket serverSocket,
                                                                   CommandLinePatcher... patchers) throws ExecutionException {
    return new WriteAction<ExecutionResult>() {
      @Override
      protected void run(@NotNull Result<ExecutionResult> result) throws Throwable {
        myExecutionResult = pyState.execute(executor, patchers);

        mySession = XDebuggerManager.getInstance(getProject()).
          startSession(env, new XDebugProcessStarter() {
            @NotNull
            public XDebugProcess start(@NotNull final XDebugSession session) {
              myDebugProcess =
                new PyDebugProcess(session, serverSocket, myExecutionResult.getExecutionConsole(), myExecutionResult.getProcessHandler(), isMultiprocessDebug());


              addTerminationHandlerProcessListener();



              return myDebugProcess;
            }


          });
        result.setResult(myExecutionResult);
      }
    };
  }
  protected void addTerminationHandlerProcessListener() {
    myDebugProcess.getProcessHandler().addProcessListener(new ProcessAdapter() {
      StringBuilder output = new StringBuilder();

      @Override
      public void onTextAvailable(ProcessEvent event, Key outputType) {
        output.append(event.getText());
      }

      @Override
      public void processTerminated(ProcessEvent event) {
        myTerminateSemaphore.release();
        if (event.getExitCode() != 0 && !myProcessCanTerminate) {
          Assert.fail("Process terminated unexpectedly\n" + output.toString());
        }
      }
    });
    myDebugProcess.getProcessHandler().startNotify();
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

  protected void waitForAllThreadsPause() throws InterruptedException, InvocationTargetException {
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
  protected PyDebugProcess getPyDebugProcess() {
    return (PyDebugProcess) myDebugProcess;
  }

  @Override
  protected void disposeDebugProcess() throws InterruptedException {
    if (myDebugProcess != null) {
      ProcessHandler processHandler = myDebugProcess.getProcessHandler();

      myDebugProcess.stop();

      if (myWaitForTermination) {
        // for some tests (with infinite loops, for example, it has no sense)
        waitFor(processHandler);
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
}
