// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.console;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionHelper;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.ParamsGroup;
import com.intellij.execution.configurations.PtyCommandLine;
import com.intellij.execution.console.ConsoleExecuteAction;
import com.intellij.execution.console.ConsoleHistoryController;
import com.intellij.execution.console.LanguageConsoleView;
import com.intellij.execution.process.*;
import com.intellij.execution.runners.ConsoleTitleGen;
import com.intellij.execution.target.*;
import com.intellij.execution.ui.RunContentDescriptor;
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
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.util.registry.RegistryManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.SideBorder;
import com.intellij.ui.content.Content;
import com.intellij.util.*;
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
import com.jetbrains.python.console.actions.ConsoleCopyOutputAction;
import com.jetbrains.python.console.actions.ScrollToTheEndAction;
import com.jetbrains.python.console.actions.ShowCommandQueueAction;
import com.jetbrains.python.console.actions.ShowVarsAction;
import com.jetbrains.python.console.pydev.ConsoleCommunicationListener;
import com.jetbrains.python.debugger.PyDebugRunner;
import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.PyVariableViewSettings;
import com.jetbrains.python.debugger.ValuesPolicy;
import com.jetbrains.python.debugger.settings.PyDebuggerSettings;
import com.jetbrains.python.icons.PythonIcons;
import com.jetbrains.python.psi.icons.PythonPsiApiIcons;
import com.jetbrains.python.remote.PyRemotePathMapper;
import com.jetbrains.python.remote.PyRemoteSdkAdditionalDataBase;
import com.jetbrains.python.remote.PyRemoteSocketToLocalHostProvider;
import com.jetbrains.python.remote.PythonRemoteInterpreterManager;
import com.jetbrains.python.run.*;
import com.jetbrains.python.run.target.HelpersAwareTargetEnvironmentRequest;
import com.jetbrains.python.sdk.PythonEnvUtil;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.*;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.intellij.execution.runners.AbstractConsoleRunnerWithHistory.registerActionShortcuts;
import static com.jetbrains.python.console.PyConsoleUtil.ASYNCIO_REPL_ENV;

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
  public static final @NonNls String PROJECT_ROOT = "PROJECT_ROOT";
  public static final @NonNls String CONSOLE_START_COMMAND = "import sys; print('Python %s on %s' % (sys.version, sys.platform))\n" +
                                                             "sys.path.extend([" + WORKING_DIR_AND_PYTHON_PATHS + "])\n";
  public static final @NonNls String STARTED_BY_RUNNER = "startedByRunner";
  public static final @NonNls String INLINE_OUTPUT_SUPPORTED = "INLINE_OUTPUT_SUPPORTED";
  private static final @NonNls String ASYNCIO_REPL_COMMAND = "-m asyncio";

  private static final Long WAIT_BEFORE_FORCED_CLOSE_MILLIS = 2000L;
  private static final Logger LOG = Logger.getInstance(PydevConsoleRunnerImpl.class);
  @SuppressWarnings("SpellCheckingInspection")
  public static final @NonNls String PYDEV_PYDEVCONSOLE_PY = "pydev/pydevconsole.py";
  public static final int PORTS_WAITING_TIMEOUT = 20000;
  /**
   * Everything in console is UTF-8 only, just like regular script execution
   */
  public static final Charset CONSOLE_CHARSET = StandardCharsets.UTF_8;
  private final Project myProject;
  private final @NlsContexts.TabTitle String myTitle;
  private final @Nullable String myWorkingDir;
  private final @Nullable Function<TargetEnvironment, String> myWorkingDirFunction;
  private @Nullable Sdk mySdk;
  private PydevConsoleCommunication myPydevConsoleCommunication;
  private ProcessHandler myProcessHandler;
  protected PythonConsoleExecuteActionHandler myConsoleExecuteActionHandler;
  private final List<ConsoleListener> myConsoleListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final PyConsoleType myConsoleType;
  private final @NotNull Map<String, String> myEnvironmentVariables;
  protected final @NotNull PyConsoleOptions.PyConsoleSettings myConsoleSettings;
  private final String @Nullable [] myStatementsToExecute;

  /*
   Console title used during initialization, it can be changed with Rename action
   */
  private @Nullable @NlsContexts.TabTitle String myConsoleInitTitle = null;
  private PythonConsoleView myConsoleView;

  /**
   * The function is resolved to {@link #myResolvedStatementsToExecute} against the target environment associated with this console.
   */
  private final @Nullable Function<TargetEnvironment, @NotNull String> myStatementsToExecuteFunction;

  private @Nullable String myResolvedStatementsToExecute;

  public PydevConsoleRunnerImpl(final @NotNull Project project,
                                @Nullable Sdk sdk,
                                final @NotNull PyConsoleType consoleType,
                                final @NotNull @NlsContexts.TabTitle String title,
                                @Nullable Function<TargetEnvironment, String> workingDirFunction,
                                @NotNull Map<String, String> environmentVariables,
                                @NotNull PyConsoleOptions.PyConsoleSettings settingsProvider,
                                @NotNull Function<TargetEnvironment, @NotNull String> statementsToExecuteFunction) {
    myProject = project;
    mySdk = sdk;
    myTitle = title;
    myWorkingDir = null;
    myWorkingDirFunction = workingDirFunction;
    myConsoleType = consoleType;
    myEnvironmentVariables = environmentVariables;
    myConsoleSettings = settingsProvider;
    myStatementsToExecute = null;
    myStatementsToExecuteFunction = statementsToExecuteFunction;
  }

  public PydevConsoleRunnerImpl(final @NotNull Project project,
                                @Nullable Sdk sdk,
                                final @NotNull PyConsoleType consoleType,
                                final @NotNull @NlsContexts.TabTitle String title,
                                final @Nullable String workingDir,
                                @NotNull Map<String, String> environmentVariables,
                                @NotNull PyConsoleOptions.PyConsoleSettings settingsProvider,
                                String... statementsToExecute) {
    myProject = project;
    mySdk = sdk;
    myTitle = title;
    myWorkingDir = workingDir;
    myWorkingDirFunction = null;
    myConsoleType = consoleType;
    myEnvironmentVariables = environmentVariables;
    myConsoleSettings = settingsProvider;
    myStatementsToExecute = statementsToExecute;
    myStatementsToExecuteFunction = environment -> StringUtil.join(statementsToExecute, "\n");
  }

  public PydevConsoleRunnerImpl(final @NotNull Project project,
                                @Nullable Sdk sdk,
                                final @NotNull PyConsoleType consoleType,
                                final @Nullable String workingDir,
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
                               myConsoleExecuteActionHandler, PythonIcons.Python.ExecuteCurrentStatement));
    toolbarActions.addAll(actions);
    // Attach Debugger
    toolbarActions.add(new ConnectDebuggerAction());
    // Show Queue
    toolbarActions.add(new ShowCommandQueueAction(myConsoleView));
    // Scroll to the end
    toolbarActions.add(new ScrollToTheEndAction(myConsoleView.getEditor()));
    // Separator
    toolbarActions.add(Separator.create());
    // Settings
    DefaultActionGroup settings = DefaultActionGroup.createPopupGroup(() -> PyBundle.message("pydev.console.runner.settings"));
    settings.getTemplatePresentation().setIcon(AllIcons.General.GearPlain);
    settings.add(new PyVariableViewSettings.SimplifiedView(null));
    settings.add(new PyVariableViewSettings.VariablesPolicyGroup());
    settings.add(new PyVariableViewSettings.QuotingPolicyGroup());
    toolbarActions.add(settings);
    // Output actions
    DefaultActionGroup outputActions = DefaultActionGroup.createPopupGroup(() -> PyBundle.message("pydev.console.runner.output.actions"));
    outputActions.getTemplatePresentation().setIcon(AllIcons.General.InspectionsEye);
    outputActions.add(new ShowVarsAction(myConsoleView, myPydevConsoleCommunication)); // Show Variables
    outputActions.add(new SoftWrapAction()); // Use soft wraps
    outputActions.add(Separator.create());
    outputActions.add(ConsoleHistoryController.getController(myConsoleView).getBrowseHistory()); // Console History
    outputActions.add(new ConsoleCopyOutputAction(myConsoleView)); // Copy Console Output
    toolbarActions.add(outputActions);
    // Actions without icons
    actions.add(PyConsoleUtil.createInterruptAction(myConsoleView));
    actions.add(PyConsoleUtil.createTabCompletionAction(myConsoleView));
    actions.add(createSplitLineAction());

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
      public void run(final @NotNull ProgressIndicator indicator) {
        try {
          Sdk sdk = mySdk;
          if (sdk == null) {
            throw new ExecutionException(PyBundle.message("pydev.console.python.interpreter.is.not.selected"));
          }
          initAndRun(sdk);
          indicator.setText(PyBundle.message("connecting.to.console.progress"));
          connect(getStatementsToExecute());
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

  private String @NotNull [] getStatementsToExecute() {
    if (myResolvedStatementsToExecute != null) {
      return new String[]{myResolvedStatementsToExecute};
    }
    else {
      if (myStatementsToExecute == null) {
        return ArrayUtil.EMPTY_STRING_ARRAY;
      }
      else {
        return myStatementsToExecute;
      }
    }
  }

  @Override
  public void run(boolean requestEditorFocus) {
    TransactionGuard.submitTransaction(PythonPluginDisposable.getInstance(myProject),
                                       () -> FileDocumentManager.getInstance().saveAllDocuments());

    ApplicationManager.getApplication().executeOnPooledThread(
      () -> ProgressManager.getInstance().run(new Task.Backgroundable(myProject, PyBundle.message("connecting.to.console.title"), false) {
        @Override
        public void run(final @NotNull ProgressIndicator indicator) {
          indicator.setText(PyBundle.message("connecting.to.console.progress"));
          try {
            if (mySdk == null) {
              throw new ExecutionException(PyBundle.message("pydev.console.python.interpreter.is.not.selected"));
            }
            initAndRun(mySdk);
            connect(getStatementsToExecute());
            if (requestEditorFocus) {
              myConsoleView.requestFocus();
            }
          }
          catch (final Exception e) {
            LOG.warn("Error running console", e);
            UIUtil.invokeAndWaitIfNeeded(() -> showErrorsInConsole(e));
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
    toolwindow.getComponent().putClientProperty(STARTED_BY_RUNNER, "true");
    PythonConsoleToolWindow.getInstance(myProject).init(toolwindow, contentDescriptor);
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
  protected GeneralCommandLine createCommandLine(final @NotNull Sdk sdk,
                                                 final @NotNull Map<String, String> environmentVariables,
                                                 @Nullable String workingDir, int port) {
    return doCreateConsoleCmdLine(sdk, environmentVariables, workingDir, port);
  }

  private @NotNull PythonExecution createPythonConsoleExecution(@NotNull Function<TargetEnvironment, HostPort> ideServerPort,
                                                                @NotNull PythonConsoleRunParams runParams,
                                                                @NotNull HelpersAwareTargetEnvironmentRequest helpersAwareTargetRequest) {
    return doCreatePythonConsoleExecution(ideServerPort, runParams, helpersAwareTargetRequest);
  }

  protected @NotNull PythonConsoleRunParams createConsoleRunParams(@Nullable String workingDir,
                                                                   @NotNull Sdk sdk,
                                                                   @NotNull Map<String, String> environmentVariables) {
    return new PythonConsoleRunParams(myConsoleSettings, workingDir, sdk, environmentVariables);
  }

  /**
   * To be deprecated.
   * <p>
   * The part of the legacy implementation based on {@link GeneralCommandLine}.
   */
  private @NotNull GeneralCommandLine doCreateConsoleCmdLine(@NotNull Sdk sdk,
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

  private @NotNull PythonExecution doCreatePythonConsoleExecution(@NotNull Function<TargetEnvironment, HostPort> ideServerPort,
                                                                  @NotNull PythonConsoleRunParams runParams,
                                                                  @NotNull HelpersAwareTargetEnvironmentRequest helpersAwareTargetRequest) {
    PythonExecution pythonConsoleScriptExecution =
      PydevConsoleCli.createPythonConsoleScriptInClientMode(ideServerPort, helpersAwareTargetRequest);

    PyRemoteSdkAdditionalDataBase remoteSdkAdditionalData = getRemoteAdditionalData(mySdk);
    PyRemotePathMapper pathMapper = remoteSdkAdditionalData != null
                                    ? PydevConsoleRunnerUtil.getPathMapper(myProject, myConsoleSettings, remoteSdkAdditionalData)
                                    : null;
    PythonCommandLineState.initEnvironment(myProject, pythonConsoleScriptExecution, runParams, helpersAwareTargetRequest, pathMapper,
                                           mySdk);

    if (myWorkingDirFunction != null) {
      pythonConsoleScriptExecution.setWorkingDir(myWorkingDirFunction);
    }

    return pythonConsoleScriptExecution;
  }

  private @NotNull PythonConsoleView createConsoleView(@NotNull Sdk sdk) {
    PythonConsoleView consoleView = new PythonConsoleView(myProject, myTitle, sdk, false);
    myPydevConsoleCommunication.setConsoleFile(consoleView.getVirtualFile());
    consoleView.addMessageFilter(new PythonTracebackFilter(myProject));
    consoleView.addMessageFilter(new PythonImportErrorFilter(myProject));
    return consoleView;
  }

  /**
   * The legacy implementation based on {@link GeneralCommandLine}. The new implementation based on Targets API could be found in
   * {@link #createProcessUsingTargetsAPI(Sdk)}.
   * <p>
   * The method is going to be removed when the flag {@code python.use.targets.api} is eliminated.
   */
  @ApiStatus.Obsolete
  private @NotNull ConsoleProcessCreationResult createProcess(@NotNull Sdk sdk) throws ExecutionException {
    PyRemoteSdkAdditionalDataBase remoteSdkAdditionalData = getRemoteAdditionalData(sdk);
    if (remoteSdkAdditionalData != null) {
      PythonRemoteInterpreterManager remoteInterpreterManager = PythonRemoteInterpreterManager.getInstance();
      if (remoteInterpreterManager == null) {
        throw new ExecutionException(PyBundle.message("remote.interpreter.feature.is.not.available"));
      }
      GeneralCommandLine generalCommandLine = createCommandLine(sdk, myEnvironmentVariables, myWorkingDir, 0);

      PyRemotePathMapper pathMapper = PydevConsoleRunnerUtil.getPathMapper(myProject, myConsoleSettings, remoteSdkAdditionalData);
      RemoteConsoleProcessData remoteConsoleProcessData =
        PythonConsoleRemoteProcessCreatorKt.createRemoteConsoleProcess(generalCommandLine,
                                                                       pathMapper,
                                                                       myProject, remoteSdkAdditionalData, getRunnerFileFromHelpers());
      myPydevConsoleCommunication = remoteConsoleProcessData.getPydevConsoleCommunication();

      return new LegacyRemoteSdkProcessHandlerCreator(remoteSdkAdditionalData, remoteConsoleProcessData.getProcess(),
                                                      remoteConsoleProcessData.getCommandLine(), myProject,
                                                      remoteConsoleProcessData.getSocketProvider(), myPydevConsoleCommunication,
                                                      remoteInterpreterManager);
    }
    else {
      int port = findAvailablePort(myProject, myConsoleType);

      GeneralCommandLine generalCommandLine = createCommandLine(sdk, myEnvironmentVariables, myWorkingDir, port);

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
      return new LegacyLocalProcessHandlerCreator(process, generalCommandLine.getCommandLineString(), communicationServer);
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
  private @NotNull ConsoleProcessCreationResult createProcessUsingTargetsAPI(@NotNull Sdk sdk) throws ExecutionException {
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

    addProjectAndModuleToRequest(targetEnvironmentRequest);

    PythonConsoleRunParams runParams = createConsoleRunParams(myWorkingDir, sdk, myEnvironmentVariables);
    PythonExecution pythonConsoleExecution = createPythonConsoleExecution(ideServerHostPortOnTarget, runParams, helpersAwareRequest);
    pythonConsoleExecution.setCharset(CONSOLE_CHARSET);
    pythonConsoleExecution.getEnvs().put(PythonEnvUtil.PYTHONIOENCODING, environment -> CONSOLE_CHARSET.name());
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

    myResolvedStatementsToExecute = myStatementsToExecuteFunction.apply(targetEnvironment);

    // TODO [Targets API] [regression] We should create PTY process when `PtyCommandLine.isEnabled()`
    //  (see the legacy method `doCreateConsoleCmdLine()`)

    // TODO [Targets API] Path mappings of SDK's remote additional data is not applied here
    boolean usePty = PtyCommandLine.isEnabled() && !SystemInfo.isWindows;
    TargetedCommandLine targetedCommandLine = PythonScripts.buildTargetedCommandLine(pythonConsoleExecution,
                                                                                     targetEnvironment,
                                                                                     sdk,
                                                                                     interpreterOptions
    );

    // The environment is now prepared and ide server port should be resolved, so let's start the server
    ResolvedPortBinding resolvedServerPortBinding = targetEnvironment.getLocalPortBindings().get(ideServerPortBinding);
    String ideServerHost;
    if (resolvedServerPortBinding != null) {
      ideServerHost = resolvedServerPortBinding.getLocalEndpoint().getHost();
    }
    else {
      LOG.error("The resolution of the local port binding for \"" + ideServerPort + "\" port cannot be found in the prepared environment" +
                ", falling back to \"localhost\" for the server socket binding on the local machine");
      ideServerHost = "localhost";
    }
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
    PyRemotePathMapper pathMapper =
      PydevConsoleRunnerUtil.createTargetEnvironmentPathMapper(myProject, sdk, myConsoleSettings, targetEnvironment);
    boolean isMostlySilentProcess = true;
    ProcessHandler processHandler = PyCustomProcessHandlerProvider.createProcessHandler(process, targetEnvironment, commandLineString,
                                                                                        targetedCommandLine.getCharset(), pathMapper,
                                                                                        isMostlySilentProcess);
    return new TargetProcessHandlerFactory(processHandler, process, commandLineString, communicationServer, targetEnvironment);
  }


  /**
   * @return if console is module-specific then provide it.
   */
  protected @Nullable Module getModule() {
    return null;
  }

  /**
   * Adds upload volumes to request, so console will have access to required files
   */
  private void addProjectAndModuleToRequest(@NotNull TargetEnvironmentRequest targetEnvironmentRequest) {
    var module = getModule();
    Module[] modules;
    if (module != null) {
      // Use module if provided
      modules = new Module[1];
      modules[0] = module;
    }
    else {
      // Use all modules otherwise
      modules = ModuleManager.getInstance(myProject).getModules();
    }
    PythonScripts.ensureProjectSdkAndModuleDirsAreOnTarget(targetEnvironmentRequest, myProject, modules);
  }

  @Contract("null -> null")
  private static @Nullable PyRemoteSdkAdditionalDataBase getRemoteAdditionalData(@Nullable Sdk sdk) {
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
                                   StreamUtil.readText(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8)),
                                   sb.toString());
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

  /**
   * Encapsulates the logic of creating {@link ProcessHandler} depending on API used to execute Python Console processes (controlled by
   * {@code python.use.targets.api} registry flag) and the type of Python interpreter.
   */
  private abstract static class ConsoleProcessCreationResult {
    protected final @NotNull Process myProcess;
    protected final @NotNull String myCommandLineString;
    protected final @NotNull PydevConsoleCommunication myConsoleCommunication;
    protected final @Nullable TargetEnvironment myTargetEnvironment;

    private ConsoleProcessCreationResult(@NotNull Process process,
                                         @NotNull String commandLineString,
                                         @NotNull PydevConsoleCommunication consoleCommunication,
                                         @Nullable TargetEnvironment environment) {
      myProcess = process;
      myCommandLineString = commandLineString;
      myConsoleCommunication = consoleCommunication;
      myTargetEnvironment = environment;
    }

    public final @Nullable TargetEnvironment getTargetEnvironment() {
      return myTargetEnvironment;
    }

    public abstract @NotNull ProcessHandler createPythonConsoleProcessHandler(@NotNull PythonConsoleView consoleView);
  }

  /**
   * Corresponds to disabled {@code python.use.targets.api} registry flag and using a local Python interpreter.
   * <p>
   * The class is going to be removed when the flag {@code python.use.targets.api} is eliminated.
   */
  @ApiStatus.Obsolete
  private static final class LegacyLocalProcessHandlerCreator extends ConsoleProcessCreationResult {
    private LegacyLocalProcessHandlerCreator(@NotNull Process process,
                                             @NotNull String commandLineString,
                                             @NotNull PydevConsoleCommunication consoleCommunication) {
      super(process, commandLineString, consoleCommunication, null);
    }

    @Override
    public @NotNull ProcessHandler createPythonConsoleProcessHandler(@NotNull PythonConsoleView consoleView) {
      return new PyConsoleProcessHandler(myProcess, consoleView, myConsoleCommunication, myCommandLineString, CONSOLE_CHARSET);
    }
  }

  /**
   * Corresponds to disabled {@code python.use.targets.api} registry flag and a remote Python interpreter with
   * {@link Sdk#getSdkAdditionalData()} of legacy {@link PyRemoteSdkAdditionalDataBase} class.
   * <p>
   * The class is going to be removed when the flag {@code python.use.targets.api} is eliminated.
   */
  @ApiStatus.Obsolete
  private static final class LegacyRemoteSdkProcessHandlerCreator extends ConsoleProcessCreationResult {
    private final @NotNull PyRemoteSdkAdditionalDataBase mySdkAdditionalData;
    private final @NotNull Project myProject;
    private final @NotNull PyRemoteSocketToLocalHostProvider mySocketProvider;
    private final @NotNull PythonRemoteInterpreterManager myRemoteInterpreterManager;

    private LegacyRemoteSdkProcessHandlerCreator(@NotNull PyRemoteSdkAdditionalDataBase data,
                                                 @NotNull Process process,
                                                 @NotNull String commandLineString,
                                                 @NotNull Project project,
                                                 @NotNull PyRemoteSocketToLocalHostProvider socketProvider,
                                                 @NotNull PydevConsoleCommunication consoleCommunication,
                                                 @NotNull PythonRemoteInterpreterManager remoteInterpreterManager) {
      super(process, commandLineString, consoleCommunication, null);
      mySdkAdditionalData = data;
      myProject = project;
      mySocketProvider = socketProvider;
      myRemoteInterpreterManager = remoteInterpreterManager;
    }

    @Override
    public @NotNull ProcessHandler createPythonConsoleProcessHandler(@NotNull PythonConsoleView consoleView) {
      return myRemoteInterpreterManager.createConsoleProcessHandler(
        myProcess, consoleView, myConsoleCommunication,
        myCommandLineString,
        CONSOLE_CHARSET,
        PythonRemoteInterpreterManager.appendBasicMappings(myProject, null, mySdkAdditionalData),
        mySocketProvider
      );
    }
  }

  /**
   * Corresponds to the enabled {@code python.use.targets.api} registry flag (which is the default now) and a local or remote Python
   * interpreter.
   */
  private static final class TargetProcessHandlerFactory extends ConsoleProcessCreationResult {
    private final @NotNull ProcessHandler myProcessHandler;

    private TargetProcessHandlerFactory(@NotNull ProcessHandler handler,
                                        @NotNull Process process,
                                        @NotNull String commandLineString,
                                        @NotNull PydevConsoleCommunication consoleCommunication,
                                        @NotNull TargetEnvironment targetEnvironment) {
      super(process, commandLineString, consoleCommunication, targetEnvironment);
      myProcessHandler = handler;
    }

    @Override
    public @NotNull ProcessHandler createPythonConsoleProcessHandler(@NotNull PythonConsoleView consoleView) {
      PyConsoleProcessHandlers.configureProcessHandlerForPythonConsole(myProcessHandler, consoleView, myConsoleCommunication);
      return myProcessHandler;
    }
  }

  private void initAndRun(@NotNull Sdk sdk) throws ExecutionException {
    // Create Server process
    ConsoleProcessCreationResult processCreationResult;
    processCreationResult = createProcessUsingTargetsAPI(sdk);
    TargetEnvironment targetEnvironment = processCreationResult.getTargetEnvironment();
    UIUtil.invokeAndWaitIfNeeded(() -> {
      // Init console view
      ApplicationManager.getApplication().runWriteIntentReadAction(() -> {
        myConsoleView = createConsoleView(sdk);
        myConsoleView.setRunner(this);
        myConsoleView.setBorder(new SideBorder(JBColor.border(), SideBorder.LEFT));
        myPydevConsoleCommunication.setConsoleView(myConsoleView);
        myProcessHandler = processCreationResult.createPythonConsoleProcessHandler(myConsoleView);

        myConsoleExecuteActionHandler = createExecuteActionHandler();

        ProcessTerminatedListener.attach(myProcessHandler);

        PythonConsoleView consoleView = myConsoleView;
        myProcessHandler.addProcessListener(new ProcessListener() {
          @Override
          public void processTerminated(@NotNull ProcessEvent event) {
            consoleView.setEditable(false);
            // PY-53068: When we send to python side command `exit` we don't get notifyFinished,
            // so we should clear Console Command Queue
            consoleView.restoreQueueWindow(true);
          }
        });

        // Attach to process
        if (targetEnvironment != null) {
          myConsoleView.setTargetEnvironment(targetEnvironment);
        }
        myConsoleView.attachToProcess(myProcessHandler);
        createContentDescriptorAndActions();

        // Run
        myProcessHandler.startNotify();
        return null;
      });
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
        @Override
        protected @NotNull List<String> getActiveConsoles(@NotNull String consoleTitle) {
          PythonConsoleToolWindow toolWindow = PythonConsoleToolWindow.getInstance(myProject);
          if (toolWindow != null && toolWindow.isInitialized()) {
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

    registerActionShortcuts(actions, myConsoleView.getConsoleEditor().getComponent());
    registerActionShortcuts(actions, mainPanel);
    getConsoleView().addConsoleFolding(false, false);

    showContentDescriptor(contentDescriptor);
  }

  private void connect(final String @NotNull [] statements2execute) {
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

        setUserTypeRenderers();

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

  private void setUserTypeRenderers() {
    myPydevConsoleCommunication.setUserTypeRenderersSettings();
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

      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
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

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
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
        ActionUtil.mergeFrom(this, CONSOLE_SPLIT_LINE_ACTION_ID);
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

  protected @NotNull PythonConsoleExecuteActionHandler createExecuteActionHandler() {
    myConsoleExecuteActionHandler =
      new PydevConsoleExecuteActionHandler(myConsoleView, myProcessHandler, myPydevConsoleCommunication);
    myConsoleExecuteActionHandler.setEnabled(false);
    new ConsoleHistoryController(PyConsoleRootType.Companion.getInstance(), "", myConsoleView, true).install();
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

  @Override
  @ApiStatus.Internal
  public void removeConsoleListener(ConsoleListener consoleListener) {
    myConsoleListeners.remove(consoleListener);
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

  private static @Nullable String getConsoleDisplayName(@NotNull Project project) {
    PythonConsoleToolWindow toolWindow = PythonConsoleToolWindow.getInstance(project);
    ToolWindow window = toolWindow.getToolWindow();
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
          UIUtil.invokeAndWaitIfNeeded(() -> closeCommunication());

          boolean processStopped = myProcessHandler.waitFor(WAIT_BEFORE_FORCED_CLOSE_MILLIS);
          if (!processStopped) {
            tryKillProcess(myProcessHandler);
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
        if (PyConsoleUtil.isCommandQueueEnabled(myProject)) {
          myConsoleView.restoreQueueWindow(true);
        }
      }
    }.queue();
  }

  private static void tryKillProcess(@NotNull ProcessHandler processHandler) {
    if (processHandler instanceof KillableProcessHandler) {
      if (((KillableProcessHandler)processHandler).canKillProcess()) {
        ((KillableProcessHandler)processHandler).killProcess();
      }
    }
  }

  @Override
  @TestOnly
  public void setSdk(@Nullable Sdk sdk) {
    mySdk = sdk;
  }

  @Override
  public @Nullable Sdk getSdk() {
    return mySdk;
  }

  private class ConnectDebuggerAction extends ToggleAction implements DumbAware {
    private boolean mySelected = false;
    private XDebugSession mySession = null;

    ConnectDebuggerAction() {
      super(PyBundle.messagePointer("console.attach.debugger"), PyBundle.messagePointer("console.attach.debugger.description"),
            PythonIcons.Python.AttachDebugger);
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
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
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

  private XDebugSession connectToDebugger() throws ExecutionException {
    final ServerSocket serverSocket = PythonCommandLineState.createServerSocket();

    return XDebuggerManager.getInstance(myProject).
      startSessionAndShowTab(PyBundle.message("pydev.console.runner.python.console.debugger"), PythonPsiApiIcons.Python, null, true,
                             new XDebugProcessStarter() {
                               @Override
                               public @NotNull XDebugProcess start(final @NotNull XDebugSession session) {
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
  public ProcessHandler getProcessHandler() {
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
    private final @NotNull Sdk mySdk;
    private final Map<String, String> myEnvironmentVariables;
    private @NotNull List<String> myEnvFiles;

    public PythonConsoleRunParams(@NotNull PyConsoleOptions.PyConsoleSettings consoleSettings,
                                  @Nullable String workingDir,
                                  @NotNull Sdk sdk,
                                  @NotNull Map<String, String> envs) {
      myConsoleSettings = consoleSettings;
      myWorkingDir = workingDir;
      mySdk = sdk;
      myEnvironmentVariables = envs;
      myEnvironmentVariables.putAll(consoleSettings.getEnvs());
      myEnvFiles = consoleSettings.myEnvFiles;
      PyDebuggerSettings debuggerSettings = PyDebuggerSettings.getInstance();
      if (debuggerSettings.getValuesPolicy() != ValuesPolicy.SYNC) {
        myEnvironmentVariables.put(PyDebugValue.POLICY_ENV_VARS.get(debuggerSettings.getValuesPolicy()), "True");
      }
      if (PyConsoleOutputCustomizer.Companion.getInstance().isInlineOutputSupported()) {
        myEnvironmentVariables.put(INLINE_OUTPUT_SUPPORTED, "True");
      }
      if (RegistryManager.getInstance().is("python.console.asyncio.repl")) {
        myEnvironmentVariables.put(ASYNCIO_REPL_ENV, "True");
      }
      if (myConsoleSettings.myInterpreterOptions.contains(ASYNCIO_REPL_COMMAND)) {
        myConsoleSettings.myInterpreterOptions = "";
      }
      PythonEnvUtil.setPythonDontWriteBytecode(myEnvironmentVariables);
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

    @Override
    public @Nullable String getSdkHome() {
      return mySdk.getHomePath();
    }

    @Override
    public @NotNull Sdk getSdk() {
      return mySdk;
    }

    @Override
    public void setSdkHome(String sdkHome) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void setSdk(@Nullable Sdk sdk) {
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

    @Override
    public @Nullable PathMappingSettings getMappingSettings() {
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

    @Override
    public @NotNull List<String> getEnvFilePaths() {
      return myEnvFiles;
    }

    @Override
    public void setEnvFilePaths(@NotNull List<String> strings) {
      myEnvFiles = strings;
    }
  }
}
