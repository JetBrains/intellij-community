// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.run;

import com.google.common.collect.Lists;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Output;
import com.intellij.execution.OutputListener;
import com.intellij.execution.RunContentExecutor;
import com.intellij.execution.configurations.EncodingEnvironmentUtil;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.ParamsGroup;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts.TabTitle;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.HelperPackage;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.buildout.BuildoutFacet;
import com.jetbrains.python.console.PydevConsoleRunner;
import com.jetbrains.python.sdk.PythonEnvUtil;
import com.jetbrains.python.sdk.PythonSdkUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TODO: Use {@link PythonRunner} instead of this class? At already supports rerun and other things
 * Base class for tasks which are run from PyCharm with results displayed in a toolwindow (manage.py, setup.py, Sphinx etc).
 *
 * @author yole
 */
public class PythonTask {
  /**
   * Mils we wait to process to be stopped when "rerun" called
   */
  private static final long TIME_TO_WAIT_PROCESS_STOP = 2000L;
  private static final int TIMEOUT_TO_WAIT_FOR_TASK = 30000;
  protected final Module myModule;
  private final Sdk mySdk;
  private String myWorkingDirectory;
  private String myRunnerScript;
  private HelperPackage myHelper = null;

  private List<String> myParameters = new ArrayList<>();
  private final @TabTitle String myRunTabTitle;
  private String myHelpId;
  private Runnable myAfterCompletion;

  public PythonTask(Module module, @TabTitle String runTabTitle) throws ExecutionException {
    this(module, runTabTitle, PythonSdkUtil.findPythonSdk(module));
  }

  @NotNull
  public static PythonTask create(@NotNull final Module module,
                                  @Nls @NotNull final String runTabTitle,
                                  @NotNull final Sdk sdk) {
    // Ctor throws checked exception which is not good, so this wrapper saves user from dumb code
    try {
      return new PythonTask(module, runTabTitle, sdk);
    }
    catch (final ExecutionException ignored) {
      throw new AssertionError("Exception thrown file should not be");
    }
  }

  public PythonTask(final Module module, @TabTitle String runTabTitle, @Nullable final Sdk sdk) throws ExecutionException {
    myModule = module;
    myRunTabTitle = runTabTitle;
    mySdk = sdk;
    if (mySdk == null) { // TODO: Get rid of such a weird contract
      throw new ExecutionException(PyBundle.message("python.task.cannot.find.python.interpreter.for.selected.module"));
    }
  }

  public String getWorkingDirectory() {
    return myWorkingDirectory;
  }

  public void setWorkingDirectory(String workingDirectory) {
    myWorkingDirectory = workingDirectory;
  }

  public void setRunnerScript(String script) {
    myRunnerScript = script;
  }

  public void setHelper(HelperPackage helper) {
    myHelper = helper;
  }

  public void setParameters(List<String> parameters) {
    myParameters = parameters;
  }

  public void setHelpId(String helpId) {
    myHelpId = helpId;
  }

  public void setAfterCompletion(Runnable afterCompletion) {
    myAfterCompletion = afterCompletion;
  }

  /**
   * @param env environment variables to be passed to process or null if nothing should be passed
   */
  public ProcessHandler createProcess(@Nullable final Map<String, String> env) throws ExecutionException {
    final GeneralCommandLine commandLine = createCommandLine();
    if (env != null) {
      commandLine.getEnvironment().putAll(env);
    }
    PydevConsoleRunner.setCorrectStdOutEncoding(commandLine, myModule.getProject()); // To support UTF-8 output

    ProcessHandler handler;
    if (PythonSdkUtil.isRemote(mySdk)) {
      assert mySdk != null;
      // give the hint for Docker Compose process starter that this process should be run with `docker-compose run` command
      // (yep, this is hacky)
      commandLine.putUserData(PyRemoteProcessStarter.RUN_AS_AUXILIARY_PROCESS, true);
      handler = new PyRemoteProcessStarter().startRemoteProcess(mySdk, commandLine, myModule.getProject(), null);
    }
    else {
      EncodingEnvironmentUtil.setLocaleEnvironmentIfMac(commandLine);
      handler = PythonProcessRunner.createProcessHandlingCtrlC(commandLine);

      ProcessTerminatedListener.attach(handler);
    }
    return handler;
  }


  /**
   * Runs command using env vars from facet
   *
   * @param consoleView console view to be used for command or null to create new
   * @throws ExecutionException failed to execute command
   */
  public void run(@Nullable final ConsoleView consoleView) throws ExecutionException {
    run(createCommandLine().getEnvironment(), consoleView);
  }

  public GeneralCommandLine createCommandLine() {
    GeneralCommandLine cmd = new GeneralCommandLine();

    if (myWorkingDirectory != null) {
      cmd.setWorkDirectory(myWorkingDirectory);
    }

    String homePath = mySdk.getHomePath();
    if (homePath != null) {
      homePath = FileUtil.toSystemDependentName(homePath);
    }

    PythonCommandLineState.createStandardGroups(cmd);
    ParamsGroup scriptParams = cmd.getParametersList().getParamsGroup(PythonCommandLineState.GROUP_SCRIPT);
    assert scriptParams != null;

    Map<String, String> env = cmd.getEnvironment();
    if (!SystemInfo.isWindows && !PythonSdkUtil.isRemote(mySdk)) {
      cmd.setExePath("bash");
      ParamsGroup bashParams = cmd.getParametersList().addParamsGroupAt(0, "Bash");
      bashParams.addParameter("-cl");

      NotNullFunction<String, String> escaperFunction = StringUtil.escaper(false, "|>$\"'& ");
      StringBuilder paramString;
      if (myHelper != null) {
        paramString = new StringBuilder(escaperFunction.fun(homePath) + " " + escaperFunction.fun(myHelper.asParamString()));
        myHelper.addToPythonPath(cmd.getEnvironment());
      }
      else {
        paramString = new StringBuilder(escaperFunction.fun(homePath) + " " + escaperFunction.fun(myRunnerScript));
      }
      for (String p : myParameters) {
        paramString.append(" ").append(p);
      }
      bashParams.addParameter(paramString.toString());
    }
    else {
      cmd.setExePath(homePath);
      if (myHelper != null) {
        myHelper.addToGroup(scriptParams, cmd);
      }
      else {
        scriptParams.addParameter(myRunnerScript);
      }
      scriptParams.addParameters(ContainerUtil.filter(myParameters, o -> o != null));
    }

    PythonEnvUtil.setPythonUnbuffered(env);
    if (homePath != null) {
      PythonEnvUtil.resetHomePathChanges(homePath, env);
    }

    List<String> pythonPath = setupPythonPath();
    PythonCommandLineState.initPythonPath(cmd, true, pythonPath, homePath);

    BuildoutFacet facet = BuildoutFacet.getInstance(myModule);
    if (facet != null) {
      facet.patchCommandLineForBuildout(cmd);
    }

    return cmd;
  }

  protected List<String> setupPythonPath() {
    return setupPythonPath(true, true);
  }

  protected List<String> setupPythonPath(final boolean addContent, final boolean addSource) {
    final List<String> pythonPath = Lists.newArrayList(PythonCommandLineState.getAddedPaths(mySdk));
    pythonPath.addAll(PythonCommandLineState.collectPythonPath(myModule, addContent, addSource));
    return pythonPath;
  }

  /**
   * @param env         environment variables to be passed to process or null if nothing should be passed
   * @param consoleView console to run this task on. New console will be used if no console provided.
   */
  public void run(@Nullable final Map<String, String> env, @Nullable final ConsoleView consoleView) throws ExecutionException {
    final ProcessHandler process = createProcess(env);
    final Project project = myModule.getProject();
    stopProcessWhenAppClosed(process);
    new RunContentExecutor(project, process)
      .withFilter(new PythonTracebackFilter(project))
      .withConsole(consoleView)
      .withTitle(myRunTabTitle)
      .withRerun(() -> {
        try {
          process.destroyProcess(); // Stop process before rerunning it
          if (process.waitFor(TIME_TO_WAIT_PROCESS_STOP)) {
            this.run(env, consoleView);
          }
          else {
            Messages.showErrorDialog(PyBundle.message("unable.to.stop"), myRunTabTitle);
          }
        }
        catch (ExecutionException e) {
          Messages.showErrorDialog(e.getMessage(), myRunTabTitle);
        }
      })
      .withStop(() -> process.destroyProcess(), () -> !process.isProcessTerminated()
      )
      .withAfterCompletion(myAfterCompletion)
      .withHelpId(myHelpId)
      .run();
  }

  /**
   * Adds process listener that kills process on application shutdown.
   * Listener is removed from process stopped to prevent leak
   */
  private void stopProcessWhenAppClosed(@NotNull ProcessHandler process) {
    Disposable disposable = PluginManager.getInstance().createDisposable(PythonTask.class, myModule);
    process.addProcessListener(new ProcessAdapter() {
      @Override
      public void processTerminated(@NotNull final ProcessEvent event) {
        Disposer.dispose(disposable);
      }
    }, disposable);
    ApplicationManager.getApplication().getMessageBus().connect(disposable)
      .subscribe(AppLifecycleListener.TOPIC, new AppLifecycleListener() {
        @Override
        public void appWillBeClosed(boolean isRestart) {
          process.destroyProcess();
        }
      });
  }

  /**
   * Runs task with out console
   *
   * @return stdout
   * @throws ExecutionException in case of error. Consider using {@link com.intellij.execution.util.ExecutionErrorDialog}
   */
  @NotNull
  public final String runNoConsole() throws ExecutionException {
    final ProgressManager manager = ProgressManager.getInstance();
    final Output output;
    if (SwingUtilities.isEventDispatchThread()) {
      assert !ApplicationManager.getApplication().isWriteAccessAllowed() : "This method can't run under write action";
      output = manager.runProcessWithProgressSynchronously(() -> getOutputInternal(), myRunTabTitle, false, myModule.getProject());
    }
    else {
      output = getOutputInternal();
    }
    final int exitCode = output.getExitCode();
    if (exitCode == 0) {
      return output.getStdout();
    }
    throw new ExecutionException(PyBundle.message("dialog.message.error.on.python.side.exit.code.stderr.stdout",
                                                  exitCode, output.getStderr(), output.getStdout()));
  }

  @NotNull
  private Output getOutputInternal() throws ExecutionException {
    assert !SwingUtilities.isEventDispatchThread();
    final ProcessHandler process = createProcess(new HashMap<>());
    final OutputListener listener = new OutputListener();
    process.addProcessListener(listener);
    process.startNotify();
    process.waitFor(TIMEOUT_TO_WAIT_FOR_TASK);
    return listener.getOutput();
  }
}
