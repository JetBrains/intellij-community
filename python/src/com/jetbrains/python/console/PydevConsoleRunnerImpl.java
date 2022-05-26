// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.console;

import com.intellij.application.options.RegistryManager;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionHelper;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.EncodingEnvironmentUtil;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.ParamsGroup;
import com.intellij.execution.configurations.PtyCommandLine;
import com.intellij.execution.console.ConsoleExecuteAction;
import com.intellij.execution.console.ConsoleHistoryController;
import com.intellij.execution.console.LanguageConsoleView;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.*;
import com.intellij.execution.runners.ConsoleTitleGen;
import com.intellij.execution.target.*;
import com.intellij.execution.target.value.TargetEnvironmentFunctions;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.icons.AllIcons;
import com.intellij.ide.errorTreeView.NewErrorTreeViewPanel;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.editor.actions.SplitLineAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.SideBorder;
import com.intellij.ui.content.Content;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ModalityUiUtil;
import com.intellij.util.PathMappingSettings;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.execution.ParametersListUtil;
import com.intellij.util.net.NetUtils;
import com.intellij.util.ui.JBEmptyBorder;
import com.intellij.util.ui.MessageCategory;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugProcessStarter;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PythonPluginDisposable;
import com.jetbrains.python.console.actions.ShowCommandQueueAction;
import com.jetbrains.python.console.actions.ShowVarsAction;
import com.jetbrains.python.console.pydev.ConsoleCommunicationListener;
import com.jetbrains.python.debugger.PyDebugRunner;
import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.PyVariableViewSettings;
import com.jetbrains.python.debugger.settings.PyDebuggerSettings;
import com.jetbrains.python.remote.PyRemotePathMapper;
import com.jetbrains.python.remote.PyRemoteSdkAdditionalDataBase;
import com.jetbrains.python.remote.PyRemoteSocketToLocalHostProvider;
import com.jetbrains.python.remote.PythonRemoteInterpreterManager;
import com.jetbrains.python.run.*;
import com.jetbrains.python.run.target.HelpersAwareTargetEnvironmentRequest;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import icons.PythonIcons;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.List;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.intellij.execution.runners.AbstractConsoleRunnerWithHistory.registerActionShortcuts;

/**
 * @author traff, oleg
 */
public class PydevConsoleRunnerImpl implements PydevConsoleRunner {
  /**
   * The address that IDE uses to listen for incoming connections from Python
   * Console script started in the "client mode".
   */
  private static final @NonNls String LOCALHOST = "localhost";

  public static final @NonNls String WORKING_DIR_AND_PYTHON_PATHS = "WORKING_DIR_AND_PYTHON_PATHS";
  public static final @NonNls String CONSOLE_START_COMMAND = "import sys; print('Python %s on %s' % (sys.version, sys.platform))\n" +
                                                             "sys.path.extend([" + WORKING_DIR_AND_PYTHON_PATHS + "])\n";
  public static final @NonNls String STARTED_BY_RUNNER = "startedByRunner";
  public static final @NonNls String INLINE_OUTPUT_SUPPORTED = "INLINE_OUTPUT_SUPPORTED";
  private static final Long WAIT_BEFORE_FORCED_CLOSE_MILLIS = 2000L;
  private static final Logger LOG = Logger.getInstance(PydevConsoleRunnerImpl.class);
  @SuppressWarnings("SpellCheckingInspection")
  public static final @NonNls String PYDEV_PYDEVCONSOLE_PY = "pydev/pydevconsole.py";
  public static final int PORTS_WAITING_TIMEOUT = 20000;
  private final Project myProject;
  private final @NlsContexts.TabTitle String myTitle;
  @Nullable private final String myWorkingDir;
  @Nullable private Sdk mySdk;
  private PydevConsoleCommunication myPydevConsoleCommunication;
  private PyConsoleProcessHandler myProcessHandler;
  protected PythonConsoleExecuteActionHandler myConsoleExecuteActionHandler;
  private final List<ConsoleListener> myConsoleListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final PyConsoleType myConsoleType;
  @NotNull private final Map<String, String> myEnvironmentVariables;
  @NotNull protected final PyConsoleOptions.PyConsoleSettings myConsoleSettings;
  private final String[] myStatementsToExecute;

  private RemoteConsoleProcessData myRemoteConsoleProcessData;

  /*
   Console title used during initialization, it can be changed with Rename action
   */
  @Nullable @NlsContexts.TabTitle private String myConsoleInitTitle = null;
  private PythonConsoleView myConsoleView;

  public PydevConsoleRunnerImpl(@NotNull final Project project,
                                @Nullable Sdk sdk,
                                @NotNull final PyConsoleType consoleType,
                                @NotNull final @NlsContexts.TabTitle String title,
                                @Nullable final String workingDir,
                                @NotNull Map<String, String> environmentVariables,
                                @NotNull PyConsoleOptions.PyConsoleSettings settingsProvider,
                                String... statementsToExecute) {
    myProject = project;
    mySdk = sdk;
    myTitle = title;
    myWorkingDir = workingDir;
    myConsoleType = consoleType;
    myEnvironmentVariables = environmentVariables;
    myConsoleSettings = settingsProvider;
    myStatementsToExecute = statementsToExecute;
  }

  public PydevConsoleRunnerImpl(@NotNull final Project project,
                                @Nullable Sdk sdk,
                                @NotNull final PyConsoleType consoleType,
                                @Nullable final String workingDir,
                                @NotNull Map<String, String> environmentVariables,
                                @NotNull PyConsoleOptions.PyConsoleSettings settingsProvider,
                                String... statementsToExecute) {
    this(project, sdk, consoleType, consoleType.getTitle(), workingDir, environmentVariables, settingsProvider, statementsToExecute);
  }

  public void setConsoleTitle(@NlsContexts.TabTitle String consoleTitle) {
    myConsoleInitTitle = consoleTitle;
  }

  private List<AnAction> fillRunActionsToolbar(final DefaultActionGroup toolbarActions) {
    List<AnAction> actions = new ArrayList<>();
    // Rerun
    actions.add(createRerunAction());
    // Stop
    actions.add(createStopAction());
    // Execute
    actions.add(
      new ConsoleExecuteAction(myConsoleView, myConsoleExecuteActionHandler, myConsoleExecuteActionHandler.getEmptyExecuteAction(),
                               myConsoleExecuteActionHandler));
    toolbarActions.addAll(actions);
    // Attach Debugger
    toolbarActions.add(new ConnectDebuggerAction());
    // Settings
    DefaultActionGroup settings = DefaultActionGroup.createPopupGroup(() -> PyBundle.message("pydev.console.runner.settings"));
    settings.getTemplatePresentation().setIcon(AllIcons.General.GearPlain);
    settings.add(new PyVariableViewSettings.SimplifiedView(null));
    settings.add(new PyVariableViewSettings.VariablesPolicyGroup());
    toolbarActions.add(settings);
    // New console
    toolbarActions.add(new NewConsoleAction());

    // Actions without icons
    actions.add(PyConsoleUtil.createInterruptAction(myConsoleView));
    actions.add(PyConsoleUtil.createTabCompletionAction(myConsoleView));
    actions.add(createSplitLineAction());

    return actions;
  }

  private List<AnAction> fillOutputActionsToolbar(final DefaultActionGroup toolbarActions) {
    List<AnAction> actions = new ArrayList<>();
    // Use soft wraps
    actions.add(new SoftWrapAction());
    // Scroll to the end
    actions.add(PyConsoleUtil.createScrollToEndAction(myConsoleView.getEditor()));
    // Print
    actions.add(PyConsoleUtil.createPrintAction(myConsoleView));
    // Show Variables
    actions.add(new ShowVarsAction(myConsoleView, myPydevConsoleCommunication));
    if (RegistryManager.getInstance().is("python.console.CommandQueue")) {
      // Show Queue
      actions.add(new ShowCommandQueueAction(myConsoleView));
    }
    // Console History
    actions.add(ConsoleHistoryController.getController(myConsoleView).getBrowseHistory());
    toolbarActions.addAll(actions);
    return actions;
  }

  @Override
  public void open() {
    PythonConsoleToolWindow toolWindow = PythonConsoleToolWindow.getInstance(myProject);
    if (toolWindow != null && toolWindow.isInitialized()) {
      toolWindow.getToolWindow().activate(() -> {
      }, true);
    }
    else {
      runSync(true);
    }
  }


  @Override
  public void runSync(boolean requestEditorFocus) {
    ProgressManager.getInstance().run(new Task.Backgroundable(myProject, PyBundle.message("connecting.to.console.title"), false) {
      @Override
      public void run(@NotNull final ProgressIndicator indicator) {
        try {
          Sdk sdk = mySdk;
          if (sdk == null) {
            throw new ExecutionException(PyBundle.message("pydev.console.python.interpreter.is.not.selected"));
          }
          initAndRun(sdk);
          indicator.setText(PyBundle.message("connecting.to.console.progress"));
          connect(myStatementsToExecute);
          if (requestEditorFocus) {
            myConsoleView.requestFocus();
          }
        }
        catch (ExecutionException e) {
          LOG.warn("Error running console", e);
          ApplicationManager.getApplication().invokeLater(() -> showErrorsInConsole(e));
        }
      }
    });
  }


  @Override
  public void run(boolean requestEditorFocus) {
    TransactionGuard.submitTransaction(PythonPluginDisposable.getInstance(myProject), () -> FileDocumentManager.getInstance().saveAllDocuments());

    ApplicationManager.getApplication().executeOnPooledThread(
      () -> ProgressManager.getInstance().run(new Task.Backgroundable(myProject, PyBundle.message("connecting.to.console.title"), false) {
        @Override
        public void run(@NotNull final ProgressIndicator indicator) {
          indicator.setText(PyBundle.message("connecting.to.console.progress"));
          try {
            if (mySdk == null) {
              throw new ExecutionException(PyBundle.message("pydev.console.python.interpreter.is.not.selected"));
            }
            initAndRun(mySdk);
            connect(myStatementsToExecute);
            if (requestEditorFocus) {
              myConsoleView.requestFocus();
            }
          }
          catch (final Exception e) {
            LOG.warn("Error running console", e);
            UIUtil.invokeAndWaitIfNeeded((Runnable)() -> showErrorsInConsole(e));
          }
        }
      })
    );
  }

  private void showErrorsInConsole(Exception e) {

    DefaultActionGroup actionGroup = new DefaultActionGroup(createRerunAction());

    final ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar("PydevConsoleRunnerErrors",
                                                                                        actionGroup, false);

    // Runner creating
    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(actionToolbar.getComponent(), BorderLayout.WEST);

    NewErrorTreeViewPanel errorViewPanel = new NewErrorTreeViewPanel(myProject, null, false, false, null);

    String[] messages = StringUtil.isNotEmpty(e.getMessage()) ? StringUtil.splitByLines(e.getMessage()) : ArrayUtilRt.EMPTY_STRING_ARRAY;
    if (messages.length == 0) {
      messages = new String[]{PyBundle.message("pydev.console.runner.unknown.error")};
    }

    errorViewPanel.addMessage(MessageCategory.ERROR, messages, null, -1, -1, null);
    panel.add(errorViewPanel, BorderLayout.CENTER);

    //noinspection DialogTitleCapitalization
    final RunContentDescriptor contentDescriptor = new RunContentDescriptor(
      null, myProcessHandler, panel, PyBundle.message("pydev.console.runner.error.running.console"));

    showContentDescriptor(contentDescriptor);
  }


  protected void showContentDescriptor(RunContentDescriptor contentDescriptor) {
    ToolWindow toolwindow = PythonConsoleToolWindow.getToolWindow(myProject);
    if (toolwindow != null) {
      toolwindow.getComponent().putClientProperty(STARTED_BY_RUNNER, "true");
      PythonConsoleToolWindow.getInstance(myProject).init(toolwindow, contentDescriptor);
    }
    else {
      RunContentManager.getInstance(myProject).showRunContent(getExecutor(), contentDescriptor);
    }
  }

  private static Executor getExecutor() {
    return DefaultRunExecutor.getRunExecutorInstance();
  }

  public static int findAvailablePort(@NotNull Project project, PyConsoleType consoleType) throws ExecutionException {
    try {
      // File "pydev/console/pydevconsole.py", line 223, in <module>
      // port, client_port = sys.argv[1:3]
      return NetUtils.findAvailableSocketPort();
    }
    catch (IOException e) {
      ExecutionHelper.showErrors(project, Collections.<Exception>singletonList(e), consoleType.getTitle(), null);

      throw new ExecutionException(e);
    }
  }

  /**
   * To be deprecated.
   * <p>
   * The part of the legacy implementation based on {@link GeneralCommandLine}.
   */
  protected GeneralCommandLine createCommandLine(@NotNull final Sdk sdk,
                                                 @NotNull final Map<String, String> environmentVariables,
                                                 @Nullable String workingDir, int port) {
    return doCreateConsoleCmdLine(sdk, environmentVariables, workingDir, port);
  }

  @NotNull
  private PythonExecution createPythonConsoleExecution(@NotNull Function<TargetEnvironment, HostPort> ideServerPort,
                                                       @NotNull PythonConsoleRunParams runParams,
                                                       @NotNull HelpersAwareTargetEnvironmentRequest helpersAwareTargetRequest) {
    return doCreatePythonConsoleExecution(ideServerPort, runParams, helpersAwareTargetRequest);
  }

  @NotNull
  protected PythonConsoleRunParams createConsoleRunParams(@Nullable String workingDir,
                                                          @NotNull Sdk sdk,
                                                          @NotNull Map<String, String> environmentVariables) {
    return new PythonConsoleRunParams(myConsoleSettings, workingDir, sdk, environmentVariables);
  }

  /**
   * To be deprecated.
   * <p>
   * The part of the legacy implementation based on {@link GeneralCommandLine}.
   */
  @NotNull
  private GeneralCommandLine doCreateConsoleCmdLine(@NotNull Sdk sdk,
                                                    @NotNull Map<String, String> environmentVariables,
                                                    @Nullable String workingDir, int port) {
    final PythonConsoleRunParams runParams = createConsoleRunParams(workingDir, sdk, environmentVariables);

    String title = PyBundle.message("connecting.to.console.title");
    GeneralCommandLine cmd = ProgressManager.getInstance().run(new Task.WithResult<>(myProject, title, false) {
      @Override
      protected GeneralCommandLine compute(@NotNull ProgressIndicator indicator) {
        return PythonCommandLineState.createPythonCommandLine(myProject, sdk.getSdkAdditionalData(), runParams, false,
                                                              PtyCommandLine.isEnabled() && !SystemInfo.isWindows);
      }
    });
    cmd.withWorkDirectory(myWorkingDir);

    ParamsGroup exeGroup = cmd.getParametersList().getParamsGroup(PythonCommandLineState.GROUP_EXE_OPTIONS);
    if (exeGroup != null && !runParams.getInterpreterOptions().isEmpty()) {
      exeGroup.addParametersString(runParams.getInterpreterOptions());
    }

    PydevConsoleCli.setupPythonConsoleScriptInClientMode(cmd, sdk, port);

    return cmd;
  }

  @NotNull
  private PythonExecution doCreatePythonConsoleExecution(@NotNull Function<TargetEnvironment, HostPort> ideServerPort,
                                                         @NotNull PythonConsoleRunParams runParams,
                                                         @NotNull HelpersAwareTargetEnvironmentRequest helpersAwareTargetRequest) {
    PythonExecution pythonConsoleScriptExecution =
      PydevConsoleCli.createPythonConsoleScriptInClientMode(ideServerPort, helpersAwareTargetRequest);

    TargetEnvironmentRequest targetEnvironmentRequest = helpersAwareTargetRequest.getTargetEnvironmentRequest();

    PyRemoteSdkAdditionalDataBase remoteSdkAdditionalData = getRemoteAdditionalData(mySdk);
    PyRemotePathMapper pathMapper = remoteSdkAdditionalData != null
                                    ? PydevConsoleRunner.getPathMapper(myProject, myConsoleSettings, remoteSdkAdditionalData)
                                    : null;
    PythonCommandLineState.initEnvironment(myProject, pythonConsoleScriptExecution, runParams, helpersAwareTargetRequest, pathMapper);

    if (myWorkingDir != null) {
      Function<TargetEnvironment, String> targetWorkingDir =
        TargetEnvironmentFunctions.getTargetEnvironmentValueForLocalPath(targetEnvironmentRequest, myWorkingDir);
      pythonConsoleScriptExecution.setWorkingDir(targetWorkingDir);
    }

    return pythonConsoleScriptExecution;
  }

  @NotNull
  private PythonConsoleView createConsoleView(@NotNull Sdk sdk) {
    PythonConsoleView consoleView = new PythonConsoleView(myProject, myTitle, sdk, false);
    myPydevConsoleCommunication.setConsoleFile(consoleView.getVirtualFile());
    consoleView.addMessageFilter(new PythonTracebackFilter(myProject));
    return consoleView;
  }

  /**
   * To be deprecated.
   * <p>
   * The legacy implementation based on {@link GeneralCommandLine}. The new
   * implementation based on Targets API could be found in
   * {@link #createProcessUsingTargetsAPI(Sdk)}.
   */
  @NotNull
  private CommandLineProcess createProcess(@NotNull Sdk sdk) throws ExecutionException {
    PyRemoteSdkAdditionalDataBase remoteSdkAdditionalData = getRemoteAdditionalData(sdk);
    if (remoteSdkAdditionalData != null) {
      GeneralCommandLine generalCommandLine = createCommandLine(sdk, myEnvironmentVariables, myWorkingDir, 0);

      PyRemotePathMapper pathMapper = PydevConsoleRunner.getPathMapper(myProject, myConsoleSettings, remoteSdkAdditionalData);
      RemoteConsoleProcessData remoteConsoleProcessData =
        PythonConsoleRemoteProcessCreatorKt.createRemoteConsoleProcess(generalCommandLine,
                                                                       pathMapper,
                                                                       myProject, remoteSdkAdditionalData, getRunnerFileFromHelpers());
      myRemoteConsoleProcessData = remoteConsoleProcessData;
      myPydevConsoleCommunication = remoteConsoleProcessData.getPydevConsoleCommunication();

      return new CommandLineProcess(remoteConsoleProcessData.getProcess(), remoteConsoleProcessData.getCommandLine());
    }
    else {
      int port = findAvailablePort(myProject, myConsoleType);

      GeneralCommandLine generalCommandLine = createCommandLine(sdk, myEnvironmentVariables, myWorkingDir, port);

      Map<String, String> envs = generalCommandLine.getEnvironment();
      EncodingEnvironmentUtil.setLocaleEnvironmentIfMac(envs, generalCommandLine.getCharset());

      PydevConsoleCommunicationServer communicationServer = new PydevConsoleCommunicationServer(myProject, LOCALHOST, port);
      myPydevConsoleCommunication = communicationServer;
      try {
        communicationServer.serve();
      }
      catch (Exception e) {
        communicationServer.close();
        throw new ExecutionException(e.getMessage(), e);
      }

      Process process = generalCommandLine.createProcess();
      communicationServer.setPythonConsoleProcess(process);
      return new CommandLineProcess(process, generalCommandLine.getCommandLineString());
    }
  }

  /**
   * This method performs several actions at once:
   * <ol>
   *   <li>Prepares the target environment determined by the provided SDK.</li>
   *   <li>Creates the server on IDE side and makes it start listening for the
   *   incoming connections.</li>
   *   <li>Starts the Python Console backend process on the target in the
   *   client mode.</li>
   * </ol>
   */
  @NotNull
  private CommandLineProcess createProcessUsingTargetsAPI(@NotNull Sdk sdk) throws ExecutionException {
    int ideServerPort;
    try {
      ideServerPort = NetUtils.findAvailableSocketPort();
    }
    catch (IOException e) {
      throw new ExecutionException(e);
    }

    HelpersAwareTargetEnvironmentRequest helpersAwareRequest = PythonCommandLineState.getPythonTargetInterpreter(myProject, sdk);
    TargetEnvironmentRequest targetEnvironmentRequest = helpersAwareRequest.getTargetEnvironmentRequest();
    TargetEnvironment.LocalPortBinding ideServerPortBinding = new TargetEnvironment.LocalPortBinding(ideServerPort, null);
    targetEnvironmentRequest.getLocalPortBindings().add(ideServerPortBinding);
    Function<TargetEnvironment, HostPort> ideServerHostPortOnTarget = targetEnvironment -> {
      ResolvedPortBinding resolvedPortBinding = targetEnvironment.getLocalPortBindings().get(ideServerPortBinding);
      if (resolvedPortBinding == null) {
        throw new IllegalStateException(MessageFormat.format("Local port binding \"{0}\" must be registered", ideServerPortBinding));
      }
      return resolvedPortBinding.getTargetEndpoint();
    };

    VirtualFile projectDir = ProjectUtil.guessProjectDir(myProject);
    if (projectDir != null) {
      // TODO [Targets API] The path where project files go should be included in Python Console Target's setup
      TargetEnvironment.TargetPath.Temporary targetPath = new TargetEnvironment.TargetPath.Temporary();
      targetEnvironmentRequest.getUploadVolumes().add(new TargetEnvironment.UploadRoot(projectDir.toNioPath(), targetPath));
    }
    else {
      LOG.warn(MessageFormat.format("Unable to guess project directory for project '{0}'." +
                                    " Project files might be unavailable in Python Console.", myProject));
    }

    PythonConsoleRunParams runParams = createConsoleRunParams(myWorkingDir, sdk, myEnvironmentVariables);
    PythonExecution pythonConsoleExecution = createPythonConsoleExecution(ideServerHostPortOnTarget, runParams, helpersAwareRequest);
    List<String> interpreterOptions;
    if (!StringUtil.isEmptyOrSpaces(runParams.getInterpreterOptions())) {
      interpreterOptions = ParametersListUtil.parse(runParams.getInterpreterOptions());
    }
    else {
      interpreterOptions = Collections.emptyList();
    }

    // extra debug options are defined at least for IronPython
    PythonSdkFlavor flavor = PythonSdkFlavor.getFlavor(sdk);
    if (flavor != null) {
      interpreterOptions.addAll(flavor.getExtraDebugOptions());
    }

    // TODO [Targets API] We should pass the proper progress indicator here
    TargetEnvironment targetEnvironment = targetEnvironmentRequest.prepareEnvironment(TargetProgressIndicator.EMPTY);

    // TODO [Targets API] [regression] We should create PTY process when `PtyCommandLine.isEnabled()`
    //  (see the legacy method `doCreateConsoleCmdLine()`)

    // TODO [Targets API] Path mappings of SDK's remote additional data is not applied here
    TargetedCommandLine targetedCommandLine = PythonScripts.buildTargetedCommandLine(pythonConsoleExecution,
                                                                                     targetEnvironment,
                                                                                     sdk,
                                                                                     interpreterOptions);

    // The environment is now prepared and ide server port should be resolved, so let's start the server
    // TODO check if binding to "localhost" properly works for Docker target on Linux host machine
    String ideServerHost = "localhost";
    PydevConsoleCommunicationServer communicationServer = new PydevConsoleCommunicationServer(myProject, ideServerHost, ideServerPort);
    myPydevConsoleCommunication = communicationServer;
    try {
      communicationServer.serve();
    }
    catch (Exception e) {
      communicationServer.close();
      throw new ExecutionException(e.getMessage(), e);
    }
    Process process = targetEnvironment.createProcess(targetedCommandLine, new EmptyProgressIndicator());
    communicationServer.setPythonConsoleProcess(process);
    String commandLineString = StringUtil.join(targetedCommandLine.getCommandPresentation(targetEnvironment), " ");
    // TODO [Targets API] [major] Python debugger in Console for SSH and Docker interpreters is effectively lost here
    KillableColoredProcessHandler processHandler = new KillableColoredProcessHandler(process, commandLineString);

    RemoteConsoleProcessData remoteConsoleProcessData = new RemoteConsoleProcessData(
      processHandler, communicationServer, commandLineString, process, new PyRemoteSocketToLocalHostProviderStub()
    );
    myRemoteConsoleProcessData = remoteConsoleProcessData;
    myPydevConsoleCommunication = remoteConsoleProcessData.getPydevConsoleCommunication();

    return new CommandLineProcess(process, commandLineString);
  }

  private static class PyRemoteSocketToLocalHostProviderStub implements PyRemoteSocketToLocalHostProvider {
    @Override
    public @NotNull Pair<String, Integer> getRemoteSocket(int localPort) {
      return Pair.create("localhost", localPort);
    }

    @Override
    public void close() throws IOException {
      // Nothing.
    }
  }

  @Contract("null -> null")
  @Nullable
  private static PyRemoteSdkAdditionalDataBase getRemoteAdditionalData(@Nullable Sdk sdk) {
    if (sdk == null) {
      return null;
    }
    else {
      SdkAdditionalData sdkAdditionalData = sdk.getSdkAdditionalData();
      if (sdkAdditionalData instanceof PyRemoteSdkAdditionalDataBase) {
        return (PyRemoteSdkAdditionalDataBase)sdkAdditionalData;
      }
      else {
        return null;
      }
    }
  }

  protected String getRunnerFileFromHelpers() {
    return PYDEV_PYDEVCONSOLE_PY;
  }

  public static int getRemotePortFromProcess(@NotNull Process process) throws ExecutionException {
    Scanner s = new Scanner(process.getInputStream());
    return readInt(s, process);
  }

  private static int readInt(Scanner s, Process process) throws ExecutionException {
    long started = System.currentTimeMillis();

    StringBuilder sb = new StringBuilder();
    boolean flag = false;

    while (System.currentTimeMillis() - started < PORTS_WAITING_TIMEOUT) {
      if (s.hasNextLine()) {
        String line = s.nextLine();
        sb.append(line).append("\n");
        try {
          int i = Integer.parseInt(line);
          if (flag) {
            LOG.warn("Unexpected strings in output:\n" + sb);
          }
          return i;
        }
        catch (NumberFormatException ignored) {
          flag = true;
          continue;
        }
      }

      TimeoutUtil.sleep(200);

      if (process.exitValue() != 0) {
        String error;
        try {
          error = PyBundle.message("pydev.console.console.process.terminated.with.error",
                                   StreamUtil.readText(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8)), sb.toString());
        }
        catch (Exception ignored) {
          error = PyBundle.message("pydev.console.console.process.terminated.with.exit.code", process.exitValue(), sb.toString());
        }
        throw new ExecutionException(error);
      }
      else {
        break;
      }
    }

    throw new ExecutionException(PyBundle.message("pydev.console.couldnt.read.integer.value.from.stream"));
  }

  private PyConsoleProcessHandler createProcessHandler(final Process process, String commandLine, @NotNull Sdk sdk) {
    SdkAdditionalData sdkAdditionalData = sdk.getSdkAdditionalData();
    if (sdkAdditionalData instanceof PyRemoteSdkAdditionalDataBase) {
      PythonRemoteInterpreterManager manager = PythonRemoteInterpreterManager.getInstance();
      if (manager != null) {
        myProcessHandler = manager.createConsoleProcessHandler(
          process, myConsoleView, myPydevConsoleCommunication,
          commandLine, StandardCharsets.UTF_8,
          PythonRemoteInterpreterManager.appendBasicMappings(myProject, null, (PyRemoteSdkAdditionalDataBase)sdkAdditionalData),
          myRemoteConsoleProcessData.getSocketProvider()
        );
      }
      else {
        LOG.error("Can't create remote console process handler");
      }
    }
    else {
      myProcessHandler = new PyConsoleProcessHandler(process, myConsoleView, myPydevConsoleCommunication, commandLine,
                                                     StandardCharsets.UTF_8);
    }
    return myProcessHandler;
  }


  private void initAndRun(@NotNull Sdk sdk) throws ExecutionException {
    // Create Server process
    CommandLineProcess commandLineProcess;
    if (Registry.get("python.use.targets.api").asBoolean()) {
      commandLineProcess = createProcessUsingTargetsAPI(sdk);
    }
    else {
      commandLineProcess = createProcess(sdk);
    }
    final Process process = commandLineProcess.getProcess();
    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
      // Init console view
      myConsoleView = createConsoleView(sdk);
      myConsoleView.setRunner(this);
      myConsoleView.setBorder(new SideBorder(JBColor.border(), SideBorder.LEFT));
      myPydevConsoleCommunication.setConsoleView(myConsoleView);
      myProcessHandler = createProcessHandler(process, commandLineProcess.getCommandLine(), sdk);

      myConsoleExecuteActionHandler = createExecuteActionHandler();

      ProcessTerminatedListener.attach(myProcessHandler);

      PythonConsoleView consoleView = myConsoleView;
      myProcessHandler.addProcessListener(new ProcessAdapter() {
        @Override
        public void processTerminated(@NotNull ProcessEvent event) {
          consoleView.setEditable(false);
        }
      });

      // Attach to process
      myConsoleView.attachToProcess(myProcessHandler);
      createContentDescriptorAndActions();

      // Run
      myProcessHandler.startNotify();
    });
  }

  protected void createContentDescriptorAndActions() {
    boolean isHorizontalAndUnitedToolbar = PyExecuteConsoleCustomizer.Companion.getInstance().isHorizontalAndUnitedToolbar();
    final DefaultActionGroup runToolbarActions = new DefaultActionGroup();
    final ActionToolbar runActionsToolbar =
      ActionManager.getInstance().createActionToolbar("PydevConsoleRunner", runToolbarActions, isHorizontalAndUnitedToolbar);
    final JPanel actionsPanel = new JPanel(new BorderLayout());
    // Left toolbar panel
    actionsPanel.add(runActionsToolbar.getComponent(), BorderLayout.WEST);

    final JPanel mainPanel = new JPanel(new BorderLayout());
    mainPanel.add(myConsoleView.getComponent(), BorderLayout.CENTER);
    myConsoleView.setToolbar(runActionsToolbar);
    runActionsToolbar.setTargetComponent(mainPanel);

    final DefaultActionGroup outputToolbarActions = new DefaultActionGroup();
    if (!isHorizontalAndUnitedToolbar) {
      final ActionToolbar outputActionsToolbar =
        ActionManager.getInstance().createActionToolbar("PydevConsoleRunner", outputToolbarActions, false);
      final JComponent outputActionsComponent = outputActionsToolbar.getComponent();
      // Add line between toolbar panels
      int emptyBorderSize = outputActionsComponent.getBorder().getBorderInsets(outputActionsComponent).left;
      outputActionsComponent.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, JBColor.border()),
                                                                          new JBEmptyBorder(emptyBorderSize)));
      // Right toolbar panel
      actionsPanel.add(outputActionsComponent, BorderLayout.CENTER);
      // Add Toolbar for PythonConsoleView
      myConsoleView.setToolbar(outputActionsToolbar);
      outputActionsToolbar.setTargetComponent(mainPanel);
      mainPanel.add(actionsPanel, BorderLayout.WEST);
    }
    else {
      mainPanel.add(actionsPanel, BorderLayout.PAGE_START);
    }

    if (myConsoleInitTitle == null) {
      ConsoleTitleGen consoleTitleGen = new ConsoleTitleGen(myProject, myTitle) {
        @NotNull
        @Override
        protected List<String> getActiveConsoles(@NotNull String consoleTitle) {
          PythonConsoleToolWindow toolWindow = PythonConsoleToolWindow.getInstance(myProject);
          if (toolWindow != null && toolWindow.isInitialized() && toolWindow.getToolWindow() != null) {
            return Arrays.stream(toolWindow.getToolWindow().getContentManager().getContents()).map(c -> c.getDisplayName())
              .filter(s -> s.startsWith(myTitle)).collect(Collectors.toList());
          }
          else {
            return super.getActiveConsoles(consoleTitle);
          }
        }
      };
      myConsoleInitTitle = consoleTitleGen.makeTitle();
    }

    final RunContentDescriptor contentDescriptor =
      new RunContentDescriptor(myConsoleView, myProcessHandler, mainPanel, myConsoleInitTitle, null);
    Disposer.register(PythonPluginDisposable.getInstance(myProject), contentDescriptor);

    contentDescriptor.setFocusComputable(() -> myConsoleView.getConsoleEditor().getContentComponent());
    contentDescriptor.setAutoFocusContent(true);

    // tool bar actions
    final List<AnAction> actions = fillRunActionsToolbar(runToolbarActions);
    final List<AnAction> outputActions;
    if (!isHorizontalAndUnitedToolbar) {
      outputActions = fillOutputActionsToolbar(outputToolbarActions);
    }
    else {
      runToolbarActions.add(new Separator());
      outputActions = fillOutputActionsToolbar(runToolbarActions);
    }
    actions.addAll(outputActions);

    registerActionShortcuts(actions, myConsoleView.getConsoleEditor().getComponent());
    registerActionShortcuts(actions, mainPanel);
    getConsoleView().addConsoleFolding(false, false);

    showContentDescriptor(contentDescriptor);
  }

  private void connect(final String[] statements2execute) {
    if (handshake()) {
      ApplicationManager.getApplication().invokeLater(() -> {
        // Propagate console communication to language console
        final PythonConsoleView consoleView = myConsoleView;

        consoleView.setConsoleCommunication(myPydevConsoleCommunication);
        consoleView.setSdk(mySdk);
        consoleView.setExecutionHandler(myConsoleExecuteActionHandler);

        enableConsoleExecuteAction();

        if (statements2execute.length == 1 && statements2execute[0].isEmpty()) {
          statements2execute[0] = "\t";
        }

        for (String statement : statements2execute) {
          consoleView.executeStatement(statement + "\n", ProcessOutputTypes.SYSTEM);
        }

        fireConsoleInitializedEvent(consoleView);
        consoleView.initialized();
      });
    }
    else {
      myConsoleView.print(PyBundle.message("pydev.console.couldnt.connect.to.console.process"), ProcessOutputTypes.STDERR);
      myProcessHandler.destroyProcess();
      myConsoleView.setEditable(false);
    }
  }

  @Override
  public AnAction createRerunAction() {
    return new RestartAction(this, myConsoleInitTitle);
  }

  private void enableConsoleExecuteAction() {
    myConsoleExecuteActionHandler.setEnabled(true);
  }

  private boolean handshake() {
    return myPydevConsoleCommunication.handshake();
  }

  private AnAction createStopAction() {
    //noinspection DialogTitleCapitalization
    return new DumbAwareAction(PyBundle.messagePointer("action.DumbAware.PydevConsoleRunnerImpl.text.stop.console"),
                               PyBundle.messagePointer("action.DumbAware.PydevConsoleRunnerImpl.description.stop.python.console"),
                               AllIcons.Actions.Suspend) {
      @Override
      public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(!isConsoleProcessTerminated());
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        stopAndRerunConsole(false, PyBundle.message("console.stopping.console"), null);
      }
    };
  }

  private class SoftWrapAction extends ToggleAction implements DumbAware {
    private boolean isSelected = myConsoleSettings.isUseSoftWraps();

    SoftWrapAction() {
      super(ActionsBundle.actionText("EditorToggleUseSoftWraps"), ActionsBundle.actionDescription("EditorToggleUseSoftWraps"),
            AllIcons.Actions.ToggleSoftWrap);
      updateEditors();
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return isSelected;
    }

    private void updateEditors() {
      myConsoleView.getEditor().getSettings().setUseSoftWraps(isSelected);
      myConsoleView.getConsoleEditor().getSettings().setUseSoftWraps(isSelected);
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      isSelected = state;
      updateEditors();
      myConsoleSettings.setUseSoftWraps(isSelected);
    }
  }

  protected AnAction createSplitLineAction() {

    class ConsoleSplitLineAction extends EditorAction {

      private static final String CONSOLE_SPLIT_LINE_ACTION_ID = "Console.SplitLine";

      ConsoleSplitLineAction() {
        super(new EditorWriteActionHandler() {

          private final SplitLineAction mySplitLineAction = new SplitLineAction();

          @Override
          public boolean isEnabledForCaret(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
            return mySplitLineAction.getHandler().isEnabled(editor, caret, dataContext);
          }

          @Override
          public void executeWriteAction(@NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
            mySplitLineAction.getHandler().execute(editor, caret, dataContext);
            editor.getCaretModel().getCurrentCaret().moveCaretRelatively(0, 1, false, true);
          }
        });
      }

      public void setup() {
        EmptyAction.setupAction(this, CONSOLE_SPLIT_LINE_ACTION_ID, null);
      }
    }

    ConsoleSplitLineAction action = new ConsoleSplitLineAction();
    action.setup();
    return action;
  }

  private void closeCommunication() {
    if (!myProcessHandler.isProcessTerminated()) {
      myPydevConsoleCommunication.close();
    }
  }

  private boolean isConsoleProcessTerminated() {
    return myProcessHandler.isProcessTerminated();
  }

  @NotNull
  protected PythonConsoleExecuteActionHandler createExecuteActionHandler() {
    myConsoleExecuteActionHandler =
      new PydevConsoleExecuteActionHandler(myConsoleView, myProcessHandler, myPydevConsoleCommunication);
    myConsoleExecuteActionHandler.setEnabled(false);
    new ConsoleHistoryController(PyConsoleRootType.Companion.getInstance(), "", myConsoleView).install();
    return myConsoleExecuteActionHandler;
  }

  @Override
  public PydevConsoleCommunication getPydevConsoleCommunication() {
    return myPydevConsoleCommunication;
  }

  static VirtualFile getConsoleFile(PsiFile psiFile) {
    VirtualFile file = psiFile.getViewProvider().getVirtualFile();
    if (file instanceof LightVirtualFile) {
      file = ((LightVirtualFile)file).getOriginalFile();
    }
    return file;
  }

  @Override
  public void addConsoleListener(ConsoleListener consoleListener) {
    myConsoleListeners.add(consoleListener);
  }

  private void fireConsoleInitializedEvent(@NotNull LanguageConsoleView consoleView) {
    for (ConsoleListener listener : myConsoleListeners) {
      listener.handleConsoleInitialized(consoleView);
    }
  }

  @Override
  public PythonConsoleExecuteActionHandler getConsoleExecuteActionHandler() {
    return myConsoleExecuteActionHandler;
  }


  private static final class RestartAction extends AnAction {
    private final PydevConsoleRunnerImpl myConsoleRunner;
    private final @NlsContexts.TabTitle String myInitTitle;

    private RestartAction(PydevConsoleRunnerImpl runner, @NlsContexts.TabTitle String initTitle) {
      ActionUtil.copyFrom(this, IdeActions.ACTION_RERUN);
      getTemplatePresentation().setIcon(AllIcons.Actions.Restart);
      myConsoleRunner = runner;
      myInitTitle = initTitle;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      String displayName = myInitTitle;
      final Project project = e.getProject();
      if (project != null) {
        final String name = getConsoleDisplayName(project);
        if (!displayName.equals(name)) {
          displayName = name;
        }
      }
      myConsoleRunner.stopAndRerunConsole(true, PyBundle.message("console.restarting.console"), displayName);
    }
  }

  @Nullable
  private static String getConsoleDisplayName(@NotNull Project project) {
    PythonConsoleToolWindow toolWindow = PythonConsoleToolWindow.getInstance(project);
    ToolWindow window = toolWindow.getToolWindow();
    if (window == null) return null;
    final Content content = window.getContentManager().getSelectedContent();
    if (content == null) return null;
    return content.getDisplayName();
  }

  @Override
  public void reRun(boolean requestEditorFocus, @NlsContexts.TabTitle String title) {
    setConsoleTitle(title);
    run(requestEditorFocus);
  }

  private void stopAndRerunConsole(Boolean rerun, @NotNull @Nls String message, @Nullable @NlsContexts.TabTitle String displayName) {
    new Task.Backgroundable(myProject, message, true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        if (myProcessHandler != null) {
          UIUtil.invokeAndWaitIfNeeded((Runnable)() -> closeCommunication());

          boolean processStopped = myProcessHandler.waitFor(WAIT_BEFORE_FORCED_CLOSE_MILLIS);
          if (!processStopped && myProcessHandler.canKillProcess()) {
            myProcessHandler.killProcess();
          }
          myProcessHandler.waitFor();
        }

        if (rerun) {
          ModalityUiUtil.invokeLaterIfNeeded(ModalityState.defaultModalityState(), () -> {
            PydevConsoleRunnerImpl.this.reRun(true, displayName);
          });
        }
        else {
          myConsoleListeners.clear();
        }
        if (RegistryManager.getInstance().is("python.console.CommandQueue")) {
          myConsoleView.restoreQueueWindow(true);
        }
      }
    }.queue();
  }

  @Override
  @TestOnly
  public void setSdk(@Nullable Sdk sdk) {
    mySdk = sdk;
  }

  private static final class CommandLineProcess {
    @NotNull
    private final Process myProcess;

    @Nullable
    private final String myCommandLine;

    private CommandLineProcess(@NotNull Process process, @Nullable String commandLine) {
      myProcess = process;
      myCommandLine = commandLine;
    }

    @NotNull
    public Process getProcess() {
      return myProcess;
    }

    @Nullable
    public String getCommandLine() {
      return myCommandLine;
    }
  }

  private class ConnectDebuggerAction extends ToggleAction implements DumbAware {
    private boolean mySelected = false;
    private XDebugSession mySession = null;

    ConnectDebuggerAction() {
      super(PyBundle.messagePointer("console.attach.debugger"), PyBundle.messagePointer("console.attach.debugger.description"),
            AllIcons.Actions.StartDebugger);
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return mySelected;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      if (mySession != null) {
        e.getPresentation().setEnabled(false);
      }
      else {
        super.update(e);
        e.getPresentation().setEnabled(true);
      }
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      mySelected = state;

      if (mySelected) {
        try {
          mySession = connectToDebugger();
        }
        catch (Exception e1) {
          LOG.error(e1);
          Messages.showErrorDialog(PyBundle.message("console.cannot.connect.to.debugger"),
                                   PyBundle.message("console.error.connecting.debugger"));
        }
      }
      else {
        //TODO: disable debugging
      }
    }
  }


  private static class NewConsoleAction extends AnAction implements DumbAware {
    NewConsoleAction() {
      super(PyBundle.messagePointer("console.new.console"), PyBundle.messagePointer("console.new.console.description"),
            AllIcons.General.Add);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(true);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final Project project = e.getData(CommonDataKeys.PROJECT);
      if (project != null) {
        PydevConsoleRunner runner =
          PythonConsoleRunnerFactory.getInstance().createConsoleRunner(project, e.getData(PlatformCoreDataKeys.MODULE));
        runner.run(true);
      }
    }
  }

  private XDebugSession connectToDebugger() throws ExecutionException {
    final ServerSocket serverSocket = PythonCommandLineState.createServerSocket();

    return XDebuggerManager.getInstance(myProject).
      startSessionAndShowTab(PyBundle.message("pydev.console.runner.python.console.debugger"), PythonIcons.Python.Python, null, true,
                             new XDebugProcessStarter() {
                               @Override
                               @NotNull
                               public XDebugProcess start(@NotNull final XDebugSession session) {
                                 PythonDebugLanguageConsoleView debugConsoleView = new PythonDebugLanguageConsoleView(myProject, mySdk);

                                 PyConsoleDebugProcessHandler consoleDebugProcessHandler =
                                   new PyConsoleDebugProcessHandler(myProcessHandler);

                                 PyConsoleDebugProcess consoleDebugProcess =
                                   new PyConsoleDebugProcess(session, serverSocket, debugConsoleView,
                                                             consoleDebugProcessHandler);

                                 PythonDebugConsoleCommunication communication =
                                   PyDebugRunner
                                     .initDebugConsoleView(myProject, consoleDebugProcess, debugConsoleView, consoleDebugProcessHandler,
                                                           session);

                                 communication.addCommunicationListener(new ConsoleCommunicationListener() {
                                   @Override
                                   public void commandExecuted(boolean more) {
                                     session.rebuildViews();
                                   }

                                   @Override
                                   public void inputRequested() {
                                   }
                                 });

                                 myPydevConsoleCommunication.setDebugCommunication(communication);
                                 debugConsoleView.attachToProcess(consoleDebugProcessHandler);

                                 consoleDebugProcess.waitForNextConnection();

                                 try {
                                   consoleDebugProcess.connect(myPydevConsoleCommunication);
                                 }
                                 catch (Exception e) {
                                   LOG.error(e); //TODO
                                 }

                                 myProcessHandler
                                   .notifyTextAvailable(PyBundle.message("pydev.console.debugger.connected"), ProcessOutputTypes.STDERR);

                                 return consoleDebugProcess;
                               }
                             });
  }

  @Override
  public PyConsoleProcessHandler getProcessHandler() {
    return myProcessHandler;
  }

  @Override
  public PythonConsoleView getConsoleView() {
    return myConsoleView;
  }

  public static PythonConsoleRunnerFactory factory() {
    return new PydevConsoleRunnerFactory();
  }

  public static class PythonConsoleRunParams implements PythonRunParams {
    private final PyConsoleOptions.PyConsoleSettings myConsoleSettings;
    private final String myWorkingDir;
    private final Sdk mySdk;
    private final Map<String, String> myEnvironmentVariables;

    public PythonConsoleRunParams(@NotNull PyConsoleOptions.PyConsoleSettings consoleSettings,
                                  @Nullable String workingDir,
                                  @NotNull Sdk sdk,
                                  @NotNull Map<String, String> envs) {
      myConsoleSettings = consoleSettings;
      myWorkingDir = workingDir;
      mySdk = sdk;
      myEnvironmentVariables = envs;
      myEnvironmentVariables.putAll(consoleSettings.getEnvs());
      PyDebuggerSettings debuggerSettings = PyDebuggerSettings.getInstance();
      if (debuggerSettings.getValuesPolicy() != PyDebugValue.ValuesPolicy.SYNC) {
        myEnvironmentVariables.put(PyDebugValue.POLICY_ENV_VARS.get(debuggerSettings.getValuesPolicy()), "True");
      }
      if (PyConsoleOutputCustomizer.Companion.getInstance().isInlineOutputSupported()) {
        myEnvironmentVariables.put(INLINE_OUTPUT_SUPPORTED, "True");
      }
    }

    @Override
    public String getInterpreterOptions() {
      return myConsoleSettings.getInterpreterOptions();
    }

    @Override
    public void setInterpreterOptions(String interpreterOptions) {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getWorkingDirectory() {
      return myWorkingDir;
    }

    @Override
    public void setWorkingDirectory(String workingDirectory) {
      throw new UnsupportedOperationException();
    }

    @Nullable
    @Override
    public String getSdkHome() {
      return mySdk.getHomePath();
    }

    @Override
    public void setSdkHome(String sdkHome) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void setModule(Module module) {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getModuleName() {
      return myConsoleSettings.getModuleName();
    }

    @Override
    public boolean isUseModuleSdk() {
      return myConsoleSettings.isUseModuleSdk();
    }

    @Override
    public void setUseModuleSdk(boolean useModuleSdk) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isPassParentEnvs() {
      return myConsoleSettings.isPassParentEnvs();
    }

    @Override
    public void setPassParentEnvs(boolean passParentEnvs) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Map<String, String> getEnvs() {
      return myEnvironmentVariables;
    }

    @Override
    public void setEnvs(Map<String, String> envs) {
      throw new UnsupportedOperationException();
    }

    @Nullable
    @Override
    public PathMappingSettings getMappingSettings() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void setMappingSettings(@Nullable PathMappingSettings mappingSettings) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean shouldAddContentRoots() {
      return myConsoleSettings.shouldAddContentRoots();
    }

    @Override
    public boolean shouldAddSourceRoots() {
      return myConsoleSettings.shouldAddSourceRoots();
    }

    @Override
    public void setAddContentRoots(boolean flag) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void setAddSourceRoots(boolean flag) {
      throw new UnsupportedOperationException();
    }
  }
}