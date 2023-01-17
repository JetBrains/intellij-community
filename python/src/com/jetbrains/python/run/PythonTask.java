// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.run;

import com.google.common.collect.Lists;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Output;
import com.intellij.execution.OutputListener;
import com.intellij.execution.RunContentExecutor;
import com.intellij.execution.configurations.EncodingEnvironmentUtil;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.ParamsGroup;
import com.intellij.execution.process.*;
import com.intellij.execution.target.TargetEnvironment;
import com.intellij.execution.target.TargetEnvironmentRequest;
import com.intellij.execution.target.TargetProgressIndicator;
import com.intellij.execution.target.value.TargetEnvironmentFunctions;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts.TabTitle;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.viewModel.extraction.ToolWindowContentExtractor;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.HelperPackage;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.console.PydevConsoleRunnerImpl;
import com.jetbrains.python.console.PydevConsoleRunnerUtil;
import com.jetbrains.python.remote.PyRemoteSdkAdditionalData;
import com.jetbrains.python.sdk.PyRemoteSdkAdditionalDataMarker;
import com.jetbrains.python.sdk.PythonEnvUtil;
import com.jetbrains.python.sdk.PythonSdkUtil;
import com.jetbrains.python.target.PyTargetAwareAdditionalData;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Base class for tasks which are run from PyCharm with results displayed in a toolwindow (manage.py, setup.py, Sphinx etc).
 * This class was written long before targets API was invented hence it doesn't provide API to mark upload/download volumes.
 * You should use Targets API for new code, but for the old code this class provides <strong>limited</strong> targets support.
 * <br/>
 * If you still need to use this class for targets api: Use {@link #addPathParameter(Path)} for paths so they would be uploaded before call
 * <p>
 * Redundant uploads/downloads are another reason to use Targets API directly
 *
 * @deprecated This class doesn't provide full support for Targets API but does it best to emulate it in some cases. Use targets API directly.
 */
@Deprecated
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

  /**
   * Indices of {@link #myParameters} which store paths (would be used for targets api call)
   */
  private final List<Integer> myPathParameterIndices = new ArrayList<>();
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

  /**
   * Add parameter and treat it as path. It would be converted to the target path is {@link #mySdk} is target-based
   */
  public final void addPathParameter(@NotNull Path path) {
    myPathParameterIndices.add(myParameters.size());
    addParameter(path.toString());
  }

  public final void addParameter(@NotNull String parameter) {
    myParameters.add(parameter);
  }

  public void setParameters(List<String> parameters) {
    myParameters = parameters;
    myPathParameterIndices.clear();
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
  public final ProcessHandler createProcess(@Nullable final Map<String, String> env) throws ExecutionException {
    final GeneralCommandLine commandLine = createCommandLine();
    if (env != null) {
      commandLine.getEnvironment().putAll(env);
    }
    PydevConsoleRunnerUtil.setCorrectStdOutEncoding(commandLine); // To support UTF-8 output

    ProcessHandler handler;
    var additionalData = mySdk.getSdkAdditionalData();
    if (additionalData instanceof PyRemoteSdkAdditionalDataMarker) {
      // Either legacy remote or target SDK
      if (additionalData instanceof PyRemoteSdkAdditionalData) {
        handler = executeLegacyRemoteProcess(commandLine, (PyRemoteSdkAdditionalData)additionalData);
      }
      else if (additionalData instanceof PyTargetAwareAdditionalData) {
        handler = executeTargetBasedProcess((PyTargetAwareAdditionalData)additionalData);
      }
      else {
        throw new IllegalArgumentException("Unknown additional data " + additionalData);
      }
    }
    else {
      handler = executeLegacyLocalProcess(commandLine);
    }
    return handler;
  }

  /**
   * Runs task on targets API based SDK.
   * Uploads all path-based parameters ({@link #myPathParameterIndices}), runs command and downloads everything back
   */
  @NotNull
  private ProcessHandler executeTargetBasedProcess(@NotNull PyTargetAwareAdditionalData data) throws ExecutionException {
    var sdk = mySdk;
    var helper = myHelper;
    var script = myRunnerScript;
    var module = myModule;
    assert module != null : "No module set";
    assert sdk != null : "No sdk set";
    assert (helper == null) != (script == null) : "Either script or helper must be set but not both";
    PythonScriptExecution execution;
    TargetEnvironmentRequest request;

    var uploadedPaths = new HashMap<@NotNull Path, @NotNull Function<TargetEnvironment, String>>();
    if (helper != null) {
      // Special shortcut for helper: use it instead of creating environment request manually
      var helpersAwareRequest = PythonInterpreterTargetEnvironmentFactory.findPythonTargetInterpreter(sdk, module.getProject());
      assert helpersAwareRequest != null : data.getClass() + " is not supported";
      execution = PythonScripts.prepareHelperScriptExecution(helper, helpersAwareRequest);
      request = helpersAwareRequest.getTargetEnvironmentRequest();
    }
    else {
      // For scripts (not helpers) create configuration manually
      var configuration = data.getTargetEnvironmentConfiguration();
      assert configuration != null : data.getClass() + " is not supported";

      request = configuration.createEnvironmentRequest(module.getProject());
      execution = new PythonScriptExecution();
      // We do not know if script path is local or not, so we only support target script
      execution.setPythonScriptPath(environment -> script);
    }


    // All paths params must be uploaded before call
    for (int i = 0; i < myParameters.size(); i++) {
      var paramValue = myParameters.get(i);
      var isPath = myPathParameterIndices.contains(i);

      if (isPath) {
        execution.addParameter(addDirToUploadList(request, uploadedPaths, paramValue));
      }
      else {
        execution.addParameter(paramValue);
      }
    }
    // Workdir should also be uploaded
    var workDir = myWorkingDirectory;
    if (workDir != null) {
      execution.setWorkingDir(addDirToUploadList(request, uploadedPaths, workDir));
    }
    TargetEnvironment environment = request.prepareEnvironment(TargetProgressIndicator.EMPTY);
    var commandLine = PythonScripts.buildTargetedCommandLine(execution, environment, sdk, new ArrayList<>());

    for (var volume : environment.getUploadVolumes().values()) {
      try {
        volume.upload(".", TargetProgressIndicator.EMPTY);
      }
      catch (IOException e) {
        throw new ExecutionException(e);
      }
    }


    Runnable downloadVolumesAfterProcess = () -> {
      environment.shutdown();
      for (var localRemotePathMapping : uploadedPaths.entrySet()) {
        var targetPath = localRemotePathMapping.getValue().apply(environment);
        var root =
          new TargetEnvironment.DownloadRoot(localRemotePathMapping.getKey(), new TargetEnvironment.TargetPath.Persistent(targetPath));
        request.getDownloadVolumes().add(root);
      }
      try {
        // download paths after call
        var newEnvironment = request.prepareEnvironment(TargetProgressIndicator.EMPTY);
        for (var volume : newEnvironment.getDownloadVolumes().values()) {
          volume.download(".", new EmptyProgressIndicator());
        }
        newEnvironment.shutdown();
      }
      catch (ExecutionException | IOException ex) {
        throw new RuntimeException(ex);
      }
    };


    var handler = new CapturingProcessHandler(environment.createProcess(commandLine, new EmptyProgressIndicator()), StandardCharsets.UTF_8,
                                              commandLine.getCommandPresentation(environment));
    handler.addProcessListener(new OutputListener() {
      @Override
      public void processTerminated(@NotNull ProcessEvent event) {
        ProgressManager.getInstance()
          .runProcessWithProgressSynchronously(downloadVolumesAfterProcess, "...", false, module.getProject());
      }
    });
    return handler;
  }

  /**
   * Utility fun for {@link #executeTargetBasedProcess(PyTargetAwareAdditionalData)}, see usage
   */
  @NotNull
  private static Function<TargetEnvironment, String> addDirToUploadList(@NotNull TargetEnvironmentRequest request,
                                                                        @NotNull Map<@NotNull Path, @NotNull Function<TargetEnvironment, String>> uploadedPaths,
                                                                        @NotNull String dir) {
    Path path = Path.of(dir);
    var uploadRoot = new TargetEnvironment.UploadRoot(path, new TargetEnvironment.TargetPath.Temporary(), true);
    var pathFun = TargetEnvironmentFunctions.getTargetUploadPath(uploadRoot);
    uploadedPaths.put(path, pathFun);
    request.getUploadVolumes().add(uploadRoot);
    return pathFun;
  }

  @NotNull
  private static ProcessHandler executeLegacyLocalProcess(@NotNull GeneralCommandLine commandLine) throws ExecutionException {
    ProcessHandler handler;
    EncodingEnvironmentUtil.setLocaleEnvironmentIfMac(commandLine);
    handler = PythonProcessRunner.createProcessHandlingCtrlC(commandLine);

    ProcessTerminatedListener.attach(handler);
    return handler;
  }

  @NotNull
  private ProcessHandler executeLegacyRemoteProcess(@NotNull GeneralCommandLine commandLine,
                                                    @NotNull PyRemoteSdkAdditionalData additionalData)
    throws ExecutionException {
    // give the hint for Docker Compose process starter that this process should be run with `docker-compose run` command
    // (yep, this is hacky)
    commandLine.putUserData(PyRemoteProcessStarter.RUN_AS_AUXILIARY_PROCESS, true);
    return PyRemoteProcessStarter.startLegacyRemoteProcess(additionalData, commandLine,
                                                                 myModule.getProject(),
                                                                 null);
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
    process.putUserData(ToolWindowContentExtractor.SYNC_TAB_TO_GUEST, true);
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
   * Runs task without console
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
