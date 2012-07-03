package com.jetbrains.env.python.debug;

import com.intellij.execution.*;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.*;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.debugger.remote.*;
import com.jetbrains.python.debugger.remote.vfs.PyRemotePositionConverter;
import com.jetbrains.python.sdk.PySdkUtil;
import com.jetbrains.python.sdk.PythonEnvUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.Semaphore;

/**
 * @author traff
 */
public class PyRemoteDebuggerTask extends PyBaseDebuggerTask {
  private Semaphore mySessionInitializedSemaphore;

  public PyRemoteDebuggerTask() {
  }

  public PyRemoteDebuggerTask(String workingFolder, String scriptName, String scriptParameters) {
    setWorkingFolder(getTestDataPath() + workingFolder);
    setScriptName(scriptName);
    setScriptParameters(scriptParameters);
  }

  public PyRemoteDebuggerTask(String workingFolder, String scriptName) {
    this(workingFolder, scriptName, null);
  }

  public void runTestOn(final String sdkHome) throws Exception {
    final Project project = getProject();

    final ConfigurationFactory factory = PyRemoteDebugConfigurationType.getInstance().getConfigurationFactories()[0];


    final RunnerAndConfigurationSettings settings =
      RunManager.getInstance(project).createRunConfiguration("test", factory);


    PyRemoteDebugConfiguration config = (PyRemoteDebugConfiguration)settings.getConfiguration();


    new WriteAction() {
      @Override
      protected void run(Result result) throws Throwable {
        RunManagerEx.getInstanceEx(project).addConfiguration(settings, false);
        RunManagerEx.getInstanceEx(project).setSelectedConfiguration(settings);
        Assert.assertSame(settings, RunManagerEx.getInstanceEx(project).getSelectedConfiguration());
      }
    }.execute();

    final PyRemoteDebugRunner runner = (PyRemoteDebugRunner)ProgramRunnerUtil.getRunner(DefaultDebugExecutor.EXECUTOR_ID, settings);
    Assert.assertTrue(runner.canRun(DefaultDebugExecutor.EXECUTOR_ID, config));

    final ExecutionEnvironment env = new ExecutionEnvironment(runner, settings, project);
    final Executor executor = DefaultDebugExecutor.getDebugExecutorInstance();


    final PyRemoteDebugCommandLineState pyState = (PyRemoteDebugCommandLineState)config.getState(executor, env);
    assert pyState != null;

    final ServerSocket serverSocket;
    final PyRemoteDebugConfiguration conf = (PyRemoteDebugConfiguration)env.getRunProfile();
    try {
      //noinspection SocketOpenedButNotSafelyClosed
      serverSocket = new ServerSocket(conf.getPort());
    }
    catch (IOException e) {
      throw new ExecutionException("Failed to find free socket port", e);
    }

    setProcessCanTerminate(false);

    mySessionInitializedSemaphore = new Semaphore(0);

    UIUtil.invokeLaterIfNeeded(new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            try {
              final ExecutionResult res =
                pyState.execute(executor, runner);


              mySession = XDebuggerManager.getInstance(project).
                startSession(runner, env, env.getContentToReuse(), new XDebugProcessStarter() {
                  @NotNull
                  public XDebugProcess start(@NotNull final XDebugSession session) {
                    myDebugProcess =
                      new PyRemoteDebugProcess(session, serverSocket, res.getExecutionConsole(),
                                               res.getProcessHandler());
                    myDebugProcess.setPositionConverter(new PyRemotePositionConverter(myDebugProcess, conf.getMappingSettings()));

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

                    myPausedSemaphore = new Semaphore(0);

                    myTerminateSemaphore = new Semaphore(0);

                    return myDebugProcess;
                  }
                });

              mySession.addSessionListener(new XDebugSessionAdapter() {
                @Override
                public void sessionPaused() {
                  if (myPausedSemaphore != null) {
                    myPausedSemaphore.release();
                  }
                }
              });

              mySessionInitializedSemaphore.release();
            }
            catch (ExecutionException e1) {
              throw new RuntimeException(e1);
            }
          }
        });
      }
    });

    before();

    new Thread(new Runnable() {
      @Override
      public void run() {
        while (myDebugProcess == null || !myDebugProcess.isWaitingForConnection()) {
          try {
            Thread.sleep(300);
          }
          catch (InterruptedException e) {
          }
        }

        ProcessOutput output =
          PySdkUtil.getProcessOutput(getWorkingFolder(), new String[]{sdkHome, getScriptPath(), Integer.toString(serverSocket.getLocalPort())}, new String[]{
            PythonEnvUtil.PYTHONUNBUFFERED + "=x", PythonEnvUtil.PYTHONPATH+"="+ PythonHelpersLocator.getHelpersRoot()}, 0);
        checkOutput(output);
      }
    }).start();


    OutputPrinter myOutputPrinter = null;
    if (shouldPrintOutput)

    {
      myOutputPrinter = new OutputPrinter();
      myOutputPrinter.start();
    }


    waitFor(mySessionInitializedSemaphore);

    try {
      testing();
    }
    catch (Throwable e) {
      throw new RuntimeException(output(), e);
    }

    after();

    clearAllBreakpoints();

    setProcessCanTerminate(true);

    if (myOutputPrinter != null)

    {
      myOutputPrinter.stop();
    }

    finishSession();
  }

  protected void checkOutput(ProcessOutput output) {
  }

  protected void stopDebugServer() throws InterruptedException {
    if (myDebugProcess != null) {
      ProcessHandler h = myDebugProcess.getProcessHandler();
      h.destroyProcess();
      h.detachProcess();

      if (!h.isStartNotified()) {
        h.startNotify();
      }
    }
  }

  protected void disposeDebugProcess() throws InterruptedException {
    if (myDebugProcess != null) {

      myDebugProcess.stop();

      ProcessHandler h = myDebugProcess.getProcessHandler();
      if (!h.isProcessTerminated()) {

        h.destroyProcess();

        if (!waitFor(h)) {
          new Throwable("Cannot stop debugger process").printStackTrace();
        }
      }
    }
  }

}
