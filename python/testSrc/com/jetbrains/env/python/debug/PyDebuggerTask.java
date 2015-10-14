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
package com.jetbrains.env.python.debug;

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
import com.jetbrains.python.debugger.PyDebugProcess;
import com.jetbrains.python.debugger.PyDebugRunner;
import com.jetbrains.python.run.PythonCommandLineState;
import com.jetbrains.python.run.PythonConfigurationType;
import com.jetbrains.python.run.PythonRunConfiguration;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.Semaphore;

/**
 * @author traff
 */
public class PyDebuggerTask extends PyBaseDebuggerTask {

  private boolean myMultiprocessDebug = false;
  private PythonRunConfiguration myRunConfiguration;

  public PyDebuggerTask() {
    init();
  }

  public PyDebuggerTask(String workingFolder, String scriptName, String scriptParameters) {
    setWorkingFolder(getTestDataPath() + workingFolder);
    setScriptName(scriptName);
    setScriptParameters(scriptParameters);
    init();
  }

  public PyDebuggerTask(String workingFolder, String scriptName) {
    this(workingFolder, scriptName, null);
  }

  protected void init() {

  }

  public void runTestOn(String sdkHome) throws Exception {
    final Project project = getProject();

    final ConfigurationFactory factory = PythonConfigurationType.getInstance().getConfigurationFactories()[0];


    final RunnerAndConfigurationSettings settings =
      RunManager.getInstance(project).createRunConfiguration("test", factory);

    myRunConfiguration = (PythonRunConfiguration)settings.getConfiguration();

    myRunConfiguration.setSdkHome(sdkHome);
    myRunConfiguration.setScriptName(getScriptPath());
    myRunConfiguration.setWorkingDirectory(getWorkingFolder());
    myRunConfiguration.setScriptParameters(getScriptParameters());

    new WriteAction() {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        RunManagerEx.getInstanceEx(project).addConfiguration(settings, false);
        RunManagerEx.getInstanceEx(project).setSelectedConfiguration(settings);
        Assert.assertSame(settings, RunManagerEx.getInstanceEx(project).getSelectedConfiguration());
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

    before();

    setProcessCanTerminate(false);

    myTerminateSemaphore = new Semaphore(0);
    
    new WriteAction<ExecutionResult>() {
      @Override
      protected void run(@NotNull Result<ExecutionResult> result) throws Throwable {
        myExecutionResult =
          pyState.execute(executor, runner.createCommandLinePatchers(myFixture.getProject(), pyState, profile, serverLocalPort));

        mySession = XDebuggerManager.getInstance(getProject()).
          startSession(env, new XDebugProcessStarter() {
            @NotNull
            public XDebugProcess start(@NotNull final XDebugSession session) {
              myDebugProcess =
                new PyDebugProcess(session, serverSocket, myExecutionResult.getExecutionConsole(), myExecutionResult.getProcessHandler(), isMultiprocessDebug());

              myDebugProcess.getProcessHandler().addProcessListener(new ProcessAdapter() {

                @Override
                public void onTextAvailable(ProcessEvent event, Key outputType) {
                }

                @Override
                public void processTerminated(ProcessEvent event) {
                  myTerminateSemaphore.release();
                  if (event.getExitCode() != 0 && !myProcessCanTerminate) {
                    Assert.fail("Process terminated unexpectedly\n" + output());
                  }
                }
              });


              myDebugProcess.getProcessHandler().startNotify();

              return myDebugProcess;
            }
          });
        result.setResult(myExecutionResult);
      }
    }.execute().getResultObject();

    OutputPrinter myOutputPrinter = null;
    if (shouldPrintOutput) {
      myOutputPrinter = new OutputPrinter();
      myOutputPrinter.start();
    }


    myPausedSemaphore = new Semaphore(0);
    

    mySession.addSessionListener(new XDebugSessionAdapter() {
      @Override
      public void sessionPaused() {
        if (myPausedSemaphore != null) {
          myPausedSemaphore.release();
        }
      }
    });

    doTest(myOutputPrinter);
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

  @Override
  protected void disposeDebugProcess() throws InterruptedException {
    if (myDebugProcess != null) {
      ProcessHandler processHandler = myDebugProcess.getProcessHandler();

      myDebugProcess.stop();

      waitFor(processHandler);

      if (!processHandler.isProcessTerminated()) {
        killDebugProcess();
        if (!waitFor(processHandler)) {
          new Throwable("Cannot stop debugger process").printStackTrace();
        }
      }
    }
  }

  private void killDebugProcess() {
    if (myDebugProcess.getProcessHandler() instanceof KillableColoredProcessHandler) {
      KillableColoredProcessHandler h = (KillableColoredProcessHandler)myDebugProcess.getProcessHandler();

      h.killProcess();
    }
    else {
      myDebugProcess.getProcessHandler().destroyProcess();
    }
  }
}
