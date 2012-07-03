package com.jetbrains.env.python.debug;

import com.intellij.execution.*;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.process.KillableColoredProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
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

  public PyDebuggerTask() {
  }

  public PyDebuggerTask(String workingFolder, String scriptName, String scriptParameters) {
    setWorkingFolder(getTestDataPath() + workingFolder);
    setScriptName(scriptName);
    setScriptParameters(scriptParameters);
  }

  public PyDebuggerTask(String workingFolder, String scriptName) {
    this(workingFolder, scriptName, null);
  }

  public void runTestOn(String sdkHome) throws Exception {
    final Project project = getProject();

    final ConfigurationFactory factory = PythonConfigurationType.getInstance().getConfigurationFactories()[0];


    final RunnerAndConfigurationSettings settings =
      RunManager.getInstance(project).createRunConfiguration("test", factory);

    PythonRunConfiguration config = (PythonRunConfiguration)settings.getConfiguration();

    config.setSdkHome(sdkHome);
    config.setScriptName(getScriptPath());
    config.setWorkingDirectory(getWorkingFolder());
    config.setScriptParameters(getScriptParameters());

    config.setMultiprocessMode(isMultiprocessDebug());

    new WriteAction() {
      @Override
      protected void run(Result result) throws Throwable {
        RunManagerEx.getInstanceEx(project).addConfiguration(settings, false);
        RunManagerEx.getInstanceEx(project).setSelectedConfiguration(settings);
        Assert.assertSame(settings, RunManagerEx.getInstanceEx(project).getSelectedConfiguration());
      }
    }.execute();

    final PyDebugRunner runner = (PyDebugRunner)ProgramRunnerUtil.getRunner(DefaultDebugExecutor.EXECUTOR_ID, settings);
    Assert.assertTrue(runner.canRun(DefaultDebugExecutor.EXECUTOR_ID, config));

    final ExecutionEnvironment env = new ExecutionEnvironment(runner, settings, project);
    final Executor executor = DefaultDebugExecutor.getDebugExecutorInstance();


    final PythonCommandLineState pyState = (PythonCommandLineState)config.getState(executor, env);
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

    ExecutionResult result = new WriteAction<ExecutionResult>() {
      @Override
      protected void run(Result<ExecutionResult> result) throws Throwable {
        final ExecutionResult res =
          pyState.execute(executor, PyDebugRunner.createCommandLinePatchers(pyState, profile, serverLocalPort));

        mySession = XDebuggerManager.getInstance(getProject()).
          startSession(runner, env, env.getContentToReuse(), new XDebugProcessStarter() {
            @NotNull
            public XDebugProcess start(@NotNull final XDebugSession session) {
              myDebugProcess =
                new PyDebugProcess(session, serverSocket, res.getExecutionConsole(), res.getProcessHandler(), isMultiprocessDebug());

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
        result.setResult(res);
      }
    }.execute().getResultObject();

    OutputPrinter myOutputPrinter = null;
    if (shouldPrintOutput) {
      myOutputPrinter = new OutputPrinter();
      myOutputPrinter.start();
    }


    myPausedSemaphore = new Semaphore(0);
    myTerminateSemaphore = new Semaphore(0);

    mySession.addSessionListener(new XDebugSessionAdapter() {
      @Override
      public void sessionPaused() {
        if (myPausedSemaphore != null) {
          myPausedSemaphore.release();
        }
      }
    });

    try {
      testing();
    }
    catch (Throwable e) {
      throw new RuntimeException(output(), e);
    }

    after();

    clearAllBreakpoints();

    setProcessCanTerminate(true);

    if (myOutputPrinter != null) {
      myOutputPrinter.stop();
    }

    finishSession();
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

      myDebugProcess.stop();

      KillableColoredProcessHandler h = (KillableColoredProcessHandler)myDebugProcess.getProcessHandler();
      if (!h.isProcessTerminated()) {

        h.killProcess();
        if (!waitFor(h)) {
          new Throwable("Cannot stop debugger process").printStackTrace();
        }
      }
    }
  }
}
