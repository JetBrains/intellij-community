// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.console;

import com.google.common.collect.Lists;
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
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.runners.ConsoleTitleGen;
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
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.GuiUtils;
import com.intellij.ui.JBColor;
import com.intellij.ui.SideBorder;
import com.intellij.ui.content.Content;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.Consumer;
import com.intellij.util.PathMappingSettings;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.net.NetUtils;
import com.intellij.util.ui.JBEmptyBorder;
import com.intellij.util.ui.MessageCategory;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugProcessStarter;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.console.actions.ShowVarsAction;
import com.jetbrains.python.console.pydev.ConsoleCommunicationListener;
import com.jetbrains.python.debugger.PyDebugRunner;
import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.PyVariableViewSettings;
import com.jetbrains.python.debugger.settings.PyDebuggerSettings;
import com.jetbrains.python.remote.PyRemotePathMapper;
import com.jetbrains.python.remote.PyRemoteSdkAdditionalDataBase;
import com.jetbrains.python.remote.PythonRemoteInterpreterManager;
import com.jetbrains.python.run.PythonCommandLineState;
import com.jetbrains.python.run.PythonRunParams;
import com.jetbrains.python.run.PythonTracebackFilter;
import com.jetbrains.python.sdk.PythonSdkUtil;
import icons.PythonIcons;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.*;
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
  private static final Logger LOG = Logger.getInstance(PydevConsoleRunnerImpl.class);
  @SuppressWarnings("SpellCheckingInspection")
  public static final @NonNls String PYDEV_PYDEVCONSOLE_PY = "pydev/pydevconsole.py";
  public static final int PORTS_WAITING_TIMEOUT = 20000;
  private final Project myProject;
  private final String myTitle;
  @Nullable private final String myWorkingDir;
  private final Consumer<? super String> myRerunAction;
  @Nullable private final Sdk mySdk;
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
  @Nullable private String myConsoleInitTitle = null;
  private PythonConsoleView myConsoleView;

  public PydevConsoleRunnerImpl(@NotNull final Project project,
                                @Nullable Sdk sdk,
                                @NotNull final PyConsoleType consoleType,
                                @NotNull final String title,
                                @Nullable final String workingDir,
                                @NotNull Map<String, String> environmentVariables,
                                @NotNull PyConsoleOptions.PyConsoleSettings settingsProvider,
                                @NotNull Consumer<? super String> rerunAction, String... statementsToExecute) {
    myProject = project;
    mySdk = sdk;
    myTitle = title;
    myWorkingDir = workingDir;
    myConsoleType = consoleType;
    myEnvironmentVariables = environmentVariables;
    myConsoleSettings = settingsProvider;
    myStatementsToExecute = statementsToExecute;
    myRerunAction = rerunAction;
  }

  public PydevConsoleRunnerImpl(@NotNull final Project project,
                                @Nullable Sdk sdk,
                                @NotNull final PyConsoleType consoleType,
                                @Nullable final String workingDir,
                                @NotNull Map<String, String> environmentVariables,
                                @NotNull PyConsoleOptions.PyConsoleSettings settingsProvider,
                                @NotNull Consumer<? super String> rerunAction, String... statementsToExecute) {
    this(project, sdk, consoleType, consoleType.getTitle(), workingDir, environmentVariables, settingsProvider, rerunAction,
         statementsToExecute);
  }

  public void setConsoleTitle(String consoleTitle) {
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
    try {
      if (mySdk == null) {
        throw new ExecutionException(PyBundle.message("pydev.console.python.interpreter.is.not.selected"));
      }
      initAndRun(mySdk);
      ProgressManager.getInstance().run(new Task.Backgroundable(myProject, PyBundle.message("connecting.to.console.title"), false) {
        @Override
        public void run(@NotNull final ProgressIndicator indicator) {
          indicator.setText(PyBundle.message("connecting.to.console.progress"));
          connect(myStatementsToExecute);
          if (requestEditorFocus) {
            myConsoleView.requestFocus();
          }
        }
      });
    }
    catch (ExecutionException e) {
      LOG.warn("Error running console", e);
      showErrorsInConsole(e);
    }
  }


  @Override
  public void run(boolean requestEditorFocus) {
    TransactionGuard.submitTransaction(myProject, () -> FileDocumentManager.getInstance().saveAllDocuments());

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


    final RunContentDescriptor contentDescriptor =
      new RunContentDescriptor(null, myProcessHandler, panel, PyBundle.message("pydev.console.runner.error.running.console"));

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

  protected GeneralCommandLine createCommandLine(@NotNull final Sdk sdk,
                                                 @NotNull final Map<String, String> environmentVariables,
                                                 @Nullable String workingDir, int port) {
    return doCreateConsoleCmdLine(sdk, environmentVariables, workingDir, port);
  }

  protected PythonConsoleRunParams createConsoleRunParams(@Nullable String workingDir,
                                                          @NotNull Sdk sdk,
                                                          @NotNull Map<String, String> environmentVariables) {
    return new PythonConsoleRunParams(myConsoleSettings, workingDir, sdk, environmentVariables);
  }

  @NotNull
  protected GeneralCommandLine doCreateConsoleCmdLine(@NotNull Sdk sdk,
                                                      @NotNull Map<String, String> environmentVariables,
                                                      @Nullable String workingDir, int port) {
    final PythonConsoleRunParams runParams = createConsoleRunParams(workingDir, sdk, environmentVariables);

    GeneralCommandLine cmd =
      PythonCommandLineState.createPythonCommandLine(myProject, sdk.getSdkAdditionalData(), runParams, false,
                                                     PtyCommandLine.isEnabled() && !SystemInfo.isWindows);
    cmd.withWorkDirectory(myWorkingDir);

    ParamsGroup exeGroup = cmd.getParametersList().getParamsGroup(PythonCommandLineState.GROUP_EXE_OPTIONS);
    if (exeGroup != null && !runParams.getInterpreterOptions().isEmpty()) {
      exeGroup.addParametersString(runParams.getInterpreterOptions());
    }

    PydevConsoleCli.setupPythonConsoleScriptInClientMode(cmd, sdk, port);

    return cmd;
  }

  @NotNull
  private PythonConsoleView createConsoleView(@NotNull Sdk sdk) {
    PythonConsoleView consoleView = new PythonConsoleView(myProject, myTitle, sdk, false);
    myPydevConsoleCommunication.setConsoleFile(consoleView.getVirtualFile());
    consoleView.addMessageFilter(new PythonTracebackFilter(myProject));
    return consoleView;
  }

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
            LOG.warn("Unexpected strings in output:\n" + sb.toString());
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
          error = PyBundle.message("pydev.console.console.process.terminated.with.error", StreamUtil.readText(process.getErrorStream()), sb.toString());
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
    if (PythonSdkUtil.isRemote(sdk)) {
      PythonRemoteInterpreterManager manager = PythonRemoteInterpreterManager.getInstance();
      if (manager != null) {
        PyRemoteSdkAdditionalDataBase data = (PyRemoteSdkAdditionalDataBase)sdk.getSdkAdditionalData();
        assert data != null;
        myProcessHandler = manager.createConsoleProcessHandler(
          process, myConsoleView, myPydevConsoleCommunication,
          commandLine, StandardCharsets.UTF_8,
          PythonRemoteInterpreterManager.appendBasicMappings(myProject, null, data),
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
    CommandLineProcess commandLineProcess = createProcess(sdk);
    final Process process = commandLineProcess.getProcess();
    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
      // Init console view
      myConsoleView = createConsoleView(sdk);
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
    final DefaultActionGroup runToolbarActions = new DefaultActionGroup();
    final ActionToolbar runActionsToolbar = ActionManager.getInstance().createActionToolbar("PydevConsoleRunner", runToolbarActions, false);

    final DefaultActionGroup outputToolbarActions = new DefaultActionGroup();
    final ActionToolbar outputActionsToolbar =
      ActionManager.getInstance().createActionToolbar("PydevConsoleRunner", outputToolbarActions, false);

    final JPanel actionsPanel = new JPanel(new BorderLayout());
    // Left toolbar panel
    actionsPanel.add(runActionsToolbar.getComponent(), BorderLayout.WEST);
    // Add line between toolbar panels
    final JComponent outputActionsComponent = outputActionsToolbar.getComponent();
    int emptyBorderSize = outputActionsComponent.getBorder().getBorderInsets(outputActionsComponent).left;
    outputActionsComponent.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, JBColor.border()),
                                                                        new JBEmptyBorder(emptyBorderSize)));
    // Right toolbar panel
    actionsPanel.add(outputActionsComponent, BorderLayout.CENTER);

    final JPanel mainPanel = new JPanel(new BorderLayout());
    mainPanel.add(actionsPanel, BorderLayout.WEST);
    mainPanel.add(myConsoleView.getComponent(), BorderLayout.CENTER);

    runActionsToolbar.setTargetComponent(mainPanel);
    outputActionsToolbar.setTargetComponent(mainPanel);

    if (myConsoleInitTitle == null) {
      myConsoleInitTitle = new ConsoleTitleGen(myProject, myTitle) {
        @NotNull
        @Override
        protected List<String> getActiveConsoles(@NotNull String consoleTitle) {
          PythonConsoleToolWindow toolWindow = PythonConsoleToolWindow.getInstance(myProject);
          if (toolWindow != null && toolWindow.isInitialized() && toolWindow.getToolWindow() != null) {
            return Lists.newArrayList(toolWindow.getToolWindow().getContentManager().getContents()).stream().map(c -> c.getDisplayName())
              .filter(s -> s.startsWith(myTitle)).collect(Collectors.toList());
          }
          else {
            return super.getActiveConsoles(consoleTitle);
          }
        }
      }.makeTitle();
    }

    final RunContentDescriptor contentDescriptor =
      new RunContentDescriptor(myConsoleView, myProcessHandler, mainPanel, myConsoleInitTitle, null);
    Disposer.register(myProject, contentDescriptor);

    contentDescriptor.setFocusComputable(() -> myConsoleView.getConsoleEditor().getContentComponent());
    contentDescriptor.setAutoFocusContent(true);

    // tool bar actions
    final List<AnAction> actions = fillRunActionsToolbar(runToolbarActions);
    final List<AnAction> outputActions = fillOutputActionsToolbar(outputToolbarActions);
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
        myProcessHandler.addProcessListener(new ProcessAdapter() {
          @Override
          public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
            consoleView.print(event.getText(), outputType);
          }
        });

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

  protected AnAction createRerunAction() {
    return new RestartAction(this, myConsoleInitTitle);
  }

  private void enableConsoleExecuteAction() {
    myConsoleExecuteActionHandler.setEnabled(true);
  }

  private boolean handshake() {
    return myPydevConsoleCommunication.handshake();
  }

  private AnAction createStopAction() {
    return new DumbAwareAction(PyBundle.messagePointer("action.DumbAware.PydevConsoleRunnerImpl.text.stop.console"),
                               PyBundle.messagePointer("action.DumbAware.PydevConsoleRunnerImpl.description.stop.python.console"),
                               AllIcons.Actions.Suspend) {
      @Override
      public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(!isConsoleProcessTerminated());
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        stopConsole();
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

  private void stopConsole() {
    if (myPydevConsoleCommunication != null) {
      try {
        closeCommunication();
        // waiting for REPL communication before destroying process handler
        Thread.sleep(300);
      }
      catch (Exception ignored) {
        // Ignore
      }
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
          public void executeWriteAction(Editor editor, @Nullable Caret caret, DataContext dataContext) {
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
    myConsoleListeners.clear();
  }

  @Override
  public PythonConsoleExecuteActionHandler getConsoleExecuteActionHandler() {
    return myConsoleExecuteActionHandler;
  }


  private static class RestartAction extends AnAction {
    private final PydevConsoleRunnerImpl myConsoleRunner;
    private final String myInitTitle;

    private RestartAction(PydevConsoleRunnerImpl runner, String initTitle) {
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
      myConsoleRunner.rerun(displayName);
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

  private void rerun(String displayName) {
    new Task.Backgroundable(myProject, PyBundle.message("console.restarting.console"), true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        if (myProcessHandler != null) {
          UIUtil.invokeAndWaitIfNeeded((Runnable)() -> closeCommunication());

          boolean processStopped = myProcessHandler.waitFor(5000L);
          if (!processStopped && myProcessHandler.canKillProcess()) {
            myProcessHandler.killProcess();
          }
          myProcessHandler.waitFor();
        }

        GuiUtils.invokeLaterIfNeeded(() -> myRerunAction.consume(displayName), ModalityState.defaultModalityState());
      }
    }.queue();
  }

  private static class CommandLineProcess {
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
      super(PyBundle.messagePointer("console.attach.debugger"), PyBundle.messagePointer("console.attach.debugger.description"), AllIcons.Actions.StartDebugger);
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
      super(PyBundle.messagePointer("console.new.console"), PyBundle.messagePointer("console.new.console.description"), AllIcons.General.Add);
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
          PythonConsoleRunnerFactory.getInstance().createConsoleRunner(project, e.getData(LangDataKeys.MODULE));
        runner.run(true);
      }
    }
  }

  private XDebugSession connectToDebugger() throws ExecutionException {
    final ServerSocket serverSocket = PythonCommandLineState.createServerSocket();

    return XDebuggerManager.getInstance(myProject).
      startSessionAndShowTab(PyBundle.message("pydev.console.runner.python.console.debugger"), PythonIcons.Python.Python, null, true, new XDebugProcessStarter() {
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
            PyDebugRunner.initDebugConsoleView(myProject, consoleDebugProcess, debugConsoleView, consoleDebugProcessHandler, session);

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

          myProcessHandler.notifyTextAvailable(PyBundle.message("pydev.console.debugger.connected"), ProcessOutputTypes.STDERR);

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
