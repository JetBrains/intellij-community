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
package com.jetbrains.python.console;

import com.google.common.collect.Lists;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionHelper;
import com.intellij.execution.ExecutionManager;
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
import com.intellij.execution.ui.actions.CloseAction;
import com.intellij.icons.AllIcons;
import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.errorTreeView.NewErrorTreeViewPanel;
import com.intellij.idea.ActionsBundle;
import com.intellij.internal.statistic.UsageTrigger;
import com.intellij.openapi.actionSystem.*;
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
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.psi.PsiFile;
import com.intellij.remote.*;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.GuiUtils;
import com.intellij.ui.JBColor;
import com.intellij.ui.SideBorder;
import com.intellij.ui.content.Content;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.PathMappingSettings;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.net.NetUtils;
import com.intellij.util.ui.MessageCategory;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugProcessStarter;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.jetbrains.python.PythonHelper;
import com.jetbrains.python.console.actions.ShowVarsAction;
import com.jetbrains.python.console.pydev.ConsoleCommunicationListener;
import com.jetbrains.python.debugger.PyDebugRunner;
import com.jetbrains.python.debugger.PySourcePosition;
import com.jetbrains.python.remote.PyRemotePathMapper;
import com.jetbrains.python.remote.PyRemoteProcessHandlerBase;
import com.jetbrains.python.remote.PyRemoteSdkAdditionalDataBase;
import com.jetbrains.python.remote.PythonRemoteInterpreterManager;
import com.jetbrains.python.run.*;
import com.jetbrains.python.sdk.PySdkUtil;
import icons.PythonIcons;
import org.apache.xmlrpc.XmlRpcException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Collectors;

import static com.intellij.execution.runners.AbstractConsoleRunnerWithHistory.registerActionShortcuts;

/**
 * @author traff, oleg
 */
public class PydevConsoleRunnerImpl implements PydevConsoleRunner {
  public static final String WORKING_DIR_AND_PYTHON_PATHS = "WORKING_DIR_AND_PYTHON_PATHS";
  public static final String CONSOLE_START_COMMAND = "import sys; print('Python %s on %s' % (sys.version, sys.platform))\n" +
                                                     "sys.path.extend([" + WORKING_DIR_AND_PYTHON_PATHS + "])\n";
  private static final Logger LOG = Logger.getInstance(PydevConsoleRunnerImpl.class.getName());
  @SuppressWarnings("SpellCheckingInspection")
  public static final String PYDEV_PYDEVCONSOLE_PY = "pydev/pydevconsole.py";
  public static final int PORTS_WAITING_TIMEOUT = 20000;
  private static final String CONSOLE_FEATURE = "python.console";
  private static final String DOCKER_CONTAINER_PROJECT_PATH = "/opt/project";
  private final Project myProject;
  private final String myTitle;
  private final String myWorkingDir;
  private final Consumer<String> myRerunAction;
  @NotNull
  private Sdk mySdk;
  private GeneralCommandLine myGeneralCommandLine;
  protected int[] myPorts;
  private PydevConsoleCommunication myPydevConsoleCommunication;
  private PyConsoleProcessHandler myProcessHandler;
  protected PythonConsoleExecuteActionHandler myConsoleExecuteActionHandler;
  private List<ConsoleListener> myConsoleListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final PyConsoleType myConsoleType;
  private Map<String, String> myEnvironmentVariables;
  private String myCommandLine;
  @NotNull private final PyConsoleOptions.PyConsoleSettings myConsoleSettings;
  private String[] myStatementsToExecute = ArrayUtil.EMPTY_STRING_ARRAY;
  private boolean myEnableAfterConnection = true;


  private static final long HANDSHAKE_TIMEOUT = 60000;

  private PyRemoteProcessHandlerBase myRemoteProcessHandlerBase;

  private String myConsoleTitle = null;
  private PythonConsoleView myConsoleView;

  public PydevConsoleRunnerImpl(@NotNull final Project project,
                                @NotNull Sdk sdk,
                                @NotNull final PyConsoleType consoleType,
                                @Nullable final String workingDir,
                                Map<String, String> environmentVariables,
                                @NotNull PyConsoleOptions.PyConsoleSettings settingsProvider,
                                @NotNull Consumer<String> rerunAction, String... statementsToExecute) {
    myProject = project;
    mySdk = sdk;
    myTitle = consoleType.getTitle();
    myWorkingDir = workingDir;
    myConsoleType = consoleType;
    myEnvironmentVariables = environmentVariables;
    myConsoleSettings = settingsProvider;
    myStatementsToExecute = statementsToExecute;
    myRerunAction = rerunAction;
  }

  public void setConsoleTitle(String consoleTitle) {
    myConsoleTitle = consoleTitle;
  }

  public void setEnableAfterConnection(boolean enableAfterConnection) {
    myEnableAfterConnection = enableAfterConnection;
  }

  private List<AnAction> fillToolBarActions(final DefaultActionGroup toolbarActions,
                                            final RunContentDescriptor contentDescriptor) {
    //toolbarActions.add(backspaceHandlingAction);

    toolbarActions.add(createRerunAction());

    List<AnAction> actions = ContainerUtil.newArrayList();

    //stop
    actions.add(createStopAction());

    //close
    actions.add(createCloseAction(contentDescriptor));

    // run action
    actions.add(
      new ConsoleExecuteAction(myConsoleView, myConsoleExecuteActionHandler, myConsoleExecuteActionHandler.getEmptyExecuteAction(),
                               myConsoleExecuteActionHandler));

    // Help
    actions.add(CommonActionsManager.getInstance().createHelpAction("interactive_console"));

    actions.add(new SoftWrapAction());

    toolbarActions.addAll(actions);


    actions.add(0, createRerunAction());

    actions.add(PyConsoleUtil.createInterruptAction(myConsoleView));
    actions.add(PyConsoleUtil.createTabCompletionAction(myConsoleView));

    actions.add(createSplitLineAction());

    toolbarActions.add(new ShowVarsAction(myConsoleView, myPydevConsoleCommunication));
    toolbarActions.add(ConsoleHistoryController.getController(myConsoleView).getBrowseHistory());

    toolbarActions.add(new ConnectDebuggerAction());

    toolbarActions.add(new NewConsoleAction());

    return actions;
  }

  @Override
  public void open() {
    PythonConsoleToolWindow toolWindow = PythonConsoleToolWindow.getInstance(myProject);
    if (toolWindow != null) {
      toolWindow.getToolWindow().activate(() -> {}, true);
    } else {
      runSync();
    }
  }

  @Override
  public void runSync() {
    myPorts = findAvailablePorts(myProject, myConsoleType);

    assert myPorts != null;

    myGeneralCommandLine = createCommandLine(mySdk, myEnvironmentVariables, myWorkingDir, myPorts);
    myCommandLine = myGeneralCommandLine.getCommandLineString();

    try {
      initAndRun();

      ProgressManager.getInstance().run(new Task.Backgroundable(myProject, "Connecting to Console", false) {
        @Override
        public void run(@NotNull final ProgressIndicator indicator) {
          indicator.setText("Connecting to console...");
          connect(myStatementsToExecute);
        }
      });
    }
    catch (ExecutionException e) {
      LOG.warn("Error running console", e);
      showErrorsInConsole(e);
    }
  }


  @Override
  public void run() {
    TransactionGuard.submitTransaction(myProject, () -> FileDocumentManager.getInstance().saveAllDocuments());

    myPorts = findAvailablePorts(myProject, myConsoleType);

    assert myPorts != null;

    myGeneralCommandLine = createCommandLine(mySdk, myEnvironmentVariables, myWorkingDir, myPorts);
    myCommandLine = myGeneralCommandLine.getCommandLineString();

    UIUtil
      .invokeLaterIfNeeded(() -> ProgressManager.getInstance().run(new Task.Backgroundable(myProject, "Connecting to Console", false) {
        @Override
        public void run(@NotNull final ProgressIndicator indicator) {
          indicator.setText("Connecting to console...");
          try {
            initAndRun();
            connect(myStatementsToExecute);
          }
          catch (final Exception e) {
            LOG.warn("Error running console", e);
            UIUtil.invokeAndWaitIfNeeded((Runnable)() -> showErrorsInConsole(e));
          }
        }
      }));
  }

  private void showErrorsInConsole(Exception e) {

    DefaultActionGroup actionGroup = new DefaultActionGroup(createRerunAction());

    final ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar("PydevConsoleRunnerErrors",
                                                                                        actionGroup, false);

    // Runner creating
    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(actionToolbar.getComponent(), BorderLayout.WEST);

    NewErrorTreeViewPanel errorViewPanel = new NewErrorTreeViewPanel(myProject, null, false, false, null);

    String[] messages = StringUtil.isNotEmpty(e.getMessage()) ? StringUtil.splitByLines(e.getMessage()) : ArrayUtil.EMPTY_STRING_ARRAY;
    if (messages.length == 0) {
      messages = new String[]{"Unknown error"};
    }

    errorViewPanel.addMessage(MessageCategory.ERROR, messages, null, -1, -1, null);
    panel.add(errorViewPanel, BorderLayout.CENTER);


    final RunContentDescriptor contentDescriptor =
      new RunContentDescriptor(null, myProcessHandler, panel, "Error running console");

    actionGroup.add(createCloseAction(contentDescriptor));

    showContentDescriptor(contentDescriptor);
  }


  protected void showContentDescriptor(RunContentDescriptor contentDescriptor) {
    ToolWindow toolwindow = PythonConsoleToolWindow.getToolWindow(myProject);
    if (toolwindow != null) {
      PythonConsoleToolWindow.getInstance(myProject).init(toolwindow, contentDescriptor);
    }
    else {
      ExecutionManager
        .getInstance(myProject).getContentManager().showRunContent(getExecutor(), contentDescriptor);
    }
  }

  private static Executor getExecutor() {
    return DefaultRunExecutor.getRunExecutorInstance();
  }

  public static int[] findAvailablePorts(Project project, PyConsoleType consoleType) {
    final int[] ports;
    try {
      // File "pydev/console/pydevconsole.py", line 223, in <module>
      // port, client_port = sys.argv[1:3]
      ports = NetUtils.findAvailableSocketPorts(2);
    }
    catch (IOException e) {
      ExecutionHelper.showErrors(project, Collections.<Exception>singletonList(e), consoleType.getTitle(), null);
      return null;
    }
    return ports;
  }

  protected GeneralCommandLine createCommandLine(@NotNull final Sdk sdk,
                                                 @NotNull final Map<String, String> environmentVariables,
                                                 String workingDir, int[] ports) {
    return doCreateConsoleCmdLine(sdk, environmentVariables, workingDir, ports, PythonHelper.CONSOLE);
  }

  @NotNull
  protected GeneralCommandLine doCreateConsoleCmdLine(Sdk sdk,
                                                      Map<String, String> environmentVariables,
                                                      String workingDir,
                                                      int[] ports,
                                                      PythonHelper helper) {
    GeneralCommandLine cmd =
      PythonCommandLineState.createPythonCommandLine(myProject, new PythonConsoleRunParams(myConsoleSettings, workingDir, sdk,
                                                                                           environmentVariables), false,
                                                     PtyCommandLine.isEnabled() && !SystemInfo.isWindows);
    cmd.withWorkDirectory(myWorkingDir);

    ParamsGroup exeGroup = cmd.getParametersList().getParamsGroup(PythonCommandLineState.GROUP_EXE_OPTIONS);
    if (exeGroup != null && !myConsoleSettings.getInterpreterOptions().isEmpty()) {
      exeGroup.addParametersString(myConsoleSettings.getInterpreterOptions());
    }

    ParamsGroup group = cmd.getParametersList().getParamsGroup(PythonCommandLineState.GROUP_SCRIPT);
    helper.addToGroup(group, cmd);

    for (int port : ports) {
      group.addParameter(String.valueOf(port));
    }

    return cmd;
  }

  private PythonConsoleView createConsoleView() {
    PythonConsoleView consoleView = new PythonConsoleView(myProject, myTitle, mySdk);
    myPydevConsoleCommunication.setConsoleFile(consoleView.getVirtualFile());
    consoleView.addMessageFilter(new PythonTracebackFilter(myProject));
    return consoleView;
  }

  private Process createProcess() throws ExecutionException {
    if (PySdkUtil.isRemote(mySdk)) {
      PythonRemoteInterpreterManager manager = PythonRemoteInterpreterManager.getInstance();
      if (manager != null) {
        UsageTrigger.trigger(CONSOLE_FEATURE + ".remote");

        PyRemoteSdkAdditionalDataBase data = (PyRemoteSdkAdditionalDataBase)mySdk.getSdkAdditionalData();
        CredentialsType connectionType = data.getRemoteConnectionType();

        if (connectionType == CredentialsType.SSH_HOST ||
            connectionType == CredentialsType.WEB_DEPLOYMENT ||
            connectionType == CredentialsType.VAGRANT) {
          return createRemoteConsoleProcess(manager,
                                            myGeneralCommandLine.getParametersList().getArray(),
                                            myGeneralCommandLine.getEnvironment(),
                                            myGeneralCommandLine.getWorkDirectory());
        }

        RemoteConsoleProcessData remoteConsoleProcessData =
          PythonConsoleRemoteProcessCreatorKt.createRemoteConsoleProcess(manager, myGeneralCommandLine.getParametersList().getArray(),
                                                                         myGeneralCommandLine.getEnvironment(),
                                                                         myGeneralCommandLine.getWorkDirectory(),
                                                                         PydevConsoleRunner.getPathMapper(myProject, mySdk, myConsoleSettings),
                                                                         myProject, data, getRunnerFileFromHelpers());

        myRemoteProcessHandlerBase = remoteConsoleProcessData.getRemoteProcessHandlerBase();
        myCommandLine = myRemoteProcessHandlerBase.getCommandLine();
        myPydevConsoleCommunication = remoteConsoleProcessData.getPydevConsoleCommunication();

        return myRemoteProcessHandlerBase.getProcess();
      }
      throw new PythonRemoteInterpreterManager.PyRemoteInterpreterExecutionException();
    }
    else {
      myCommandLine = myGeneralCommandLine.getCommandLineString();
      Map<String, String> envs = myGeneralCommandLine.getEnvironment();
      EncodingEnvironmentUtil.setLocaleEnvironmentIfMac(envs, myGeneralCommandLine.getCharset());

      UsageTrigger.trigger(CONSOLE_FEATURE + ".local");
      final Process server = myGeneralCommandLine.createProcess();

      try {
        myPydevConsoleCommunication = new PydevConsoleCommunication(myProject, myPorts[0], server, myPorts[1]);
      }
      catch (Exception e) {
        throw new ExecutionException(e.getMessage());
      }
      return server;
    }
  }

  protected String getRunnerFileFromHelpers() {
    return PYDEV_PYDEVCONSOLE_PY;
  }

  private RemoteProcess createRemoteConsoleProcess(PythonRemoteInterpreterManager manager,
                                                   String[] command,
                                                   Map<String, String> env,
                                                   File workDirectory)
    throws ExecutionException {
    PyRemoteSdkAdditionalDataBase data = (PyRemoteSdkAdditionalDataBase)mySdk.getSdkAdditionalData();
    assert data != null;

    GeneralCommandLine commandLine = new GeneralCommandLine();

    commandLine.setWorkDirectory(workDirectory);

    commandLine.withParameters(command);

    commandLine.getEnvironment().putAll(env);

    commandLine.getParametersList().set(0, PythonRemoteInterpreterManager.toSystemDependent(new File(data.getHelpersPath(),
                                                                                                     getRunnerFileFromHelpers())
                                                                                              .getPath(),
                                                                                            PySourcePosition.isWindowsPath(
                                                                                              data.getInterpreterPath())
    ));
    commandLine.getParametersList().set(1, "0");
    commandLine.getParametersList().set(2, "0");

    try {
      PyRemotePathMapper pathMapper = PydevConsoleRunner.getPathMapper(myProject, mySdk, myConsoleSettings);

      assert pathMapper != null;

      commandLine.putUserData(PyRemoteProcessStarter.OPEN_FOR_INCOMING_CONNECTION, true);

      // we do not have an option to setup Docker container settings now for Python console so we should bind at least project
      // directory to some path inside the Docker container
      commandLine.putUserData(PythonRemoteInterpreterManager.ADDITIONAL_MAPPINGS, buildDockerPathMappings());

      myRemoteProcessHandlerBase = PyRemoteProcessStarterManagerUtil
        .getManager(data).startRemoteProcess(myProject, commandLine, manager, data,
                                             pathMapper);

      myCommandLine = myRemoteProcessHandlerBase.getCommandLine();

      RemoteProcess remoteProcess = myRemoteProcessHandlerBase.getProcess();

      Couple<Integer> remotePorts = getRemotePortsFromProcess(remoteProcess);

      if (remoteProcess instanceof Tunnelable) {
        Tunnelable tunnelableProcess = (Tunnelable)remoteProcess;
        tunnelableProcess.addLocalTunnel(myPorts[0], remotePorts.first);
        tunnelableProcess.addRemoteTunnel(remotePorts.second, "localhost", myPorts[1]);

        if (LOG.isDebugEnabled()) {
          LOG.debug(String.format("Using tunneled communication for Python console: port %d (=> %d) on IDE side, " +
                                  "port %d (=> %d) on pydevconsole.py side", myPorts[1], remotePorts.second, myPorts[0],
                                  remotePorts.first));
        }

        myPydevConsoleCommunication = new PydevRemoteConsoleCommunication(myProject, myPorts[0], remoteProcess, myPorts[1]);
      }
      else {
        if (LOG.isDebugEnabled()) {
          LOG.debug(String.format("Using direct communication for Python console: port %d on IDE side, port %d on pydevconsole.py side",
                                  remotePorts.second, remotePorts.first));
        }

        myPydevConsoleCommunication =
          new PydevRemoteConsoleCommunication(myProject, remotePorts.first, remoteProcess, remotePorts.second);
      }

      return remoteProcess;
    }
    catch (Exception e) {
      throw new ExecutionException(e.getMessage(), e);
    }
  }

  @NotNull
  private PathMappingSettings buildDockerPathMappings() {
    return new PathMappingSettings(Collections.singletonList(new PathMappingSettings.PathMapping(myProject.getBasePath(),
                                                                                                 DOCKER_CONTAINER_PROJECT_PATH)));
  }

  public static Couple<Integer> getRemotePortsFromProcess(RemoteProcess process) throws ExecutionException {
    Scanner s = new Scanner(process.getInputStream());

    return Couple.of(readInt(s, process), readInt(s, process));
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
          error = "Console process terminated with error:\n" + StreamUtil.readText(process.getErrorStream()) + sb.toString();
        }
        catch (Exception ignored) {
          error = "Console process terminated with exit code " + process.exitValue() + ", output:" + sb.toString();
        }
        throw new ExecutionException(error);
      }
      else {
        break;
      }
    }

    throw new ExecutionException("Couldn't read integer value from stream");
  }

  private PyConsoleProcessHandler createProcessHandler(final Process process) {
    if (PySdkUtil.isRemote(mySdk)) {
      PythonRemoteInterpreterManager manager = PythonRemoteInterpreterManager.getInstance();
      if (manager != null) {
        PyRemoteSdkAdditionalDataBase data = (PyRemoteSdkAdditionalDataBase)mySdk.getSdkAdditionalData();
        assert data != null;
        myProcessHandler =
          manager.createConsoleProcessHandler((RemoteProcess)process, myConsoleView, myPydevConsoleCommunication,
                                              myCommandLine, CharsetToolkit.UTF8_CHARSET,
                                              manager.setupMappings(myProject, data, null),
                                              myRemoteProcessHandlerBase.getRemoteSocketToLocalHostProvider());
      }
      else {
        LOG.error("Can't create remote console process handler");
      }
    }
    else {
      myProcessHandler = new PyConsoleProcessHandler(process, myConsoleView, myPydevConsoleCommunication, myCommandLine,
                                                     CharsetToolkit.UTF8_CHARSET);
    }
    return myProcessHandler;
  }


  private void initAndRun() throws ExecutionException {
    // Create Server process
    final Process process = createProcess();
    UIUtil.invokeLaterIfNeeded(() -> {
      // Init console view
      myConsoleView = createConsoleView();
      if (myConsoleView != null) {
        ((JComponent)myConsoleView).setBorder(new SideBorder(JBColor.border(), SideBorder.LEFT));
      }
      myProcessHandler = createProcessHandler(process);

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
    // Runner creating
    final DefaultActionGroup toolbarActions = new DefaultActionGroup();
    final ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar("PydevConsoleRunner", toolbarActions, false);

    // Runner creating
    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(actionToolbar.getComponent(), BorderLayout.WEST);
    panel.add(myConsoleView.getComponent(), BorderLayout.CENTER);

    actionToolbar.setTargetComponent(panel);

    if (myConsoleTitle == null) {
      myConsoleTitle = new ConsoleTitleGen(myProject, myTitle) {
        @NotNull
        @Override
        protected List<String> getActiveConsoles(@NotNull String consoleTitle) {
          PythonConsoleToolWindow toolWindow = PythonConsoleToolWindow.getInstance(myProject);
          if (toolWindow != null && toolWindow.getToolWindow() != null) {
            return Lists.newArrayList(toolWindow.getToolWindow().getContentManager().getContents()).stream().map(c -> c.getDisplayName())
              .collect(
                Collectors.toList());
          }
          else {
            return super.getActiveConsoles(consoleTitle);
          }
        }
      }.makeTitle();
    }

    final RunContentDescriptor contentDescriptor =
      new RunContentDescriptor(myConsoleView, myProcessHandler, panel, myConsoleTitle, null);

    contentDescriptor.setFocusComputable(() -> myConsoleView.getConsoleEditor().getContentComponent());
    contentDescriptor.setAutoFocusContent(true);


    // tool bar actions
    final List<AnAction> actions = fillToolBarActions(toolbarActions, contentDescriptor);
    registerActionShortcuts(actions, myConsoleView.getConsoleEditor().getComponent());
    registerActionShortcuts(actions, panel);
    getConsoleView().addConsoleFolding(false);

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

        if (myEnableAfterConnection) {
          enableConsoleExecuteAction();
        }

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
      myConsoleView.print("Couldn't connect to console process.", ProcessOutputTypes.STDERR);
      myProcessHandler.destroyProcess();
      myConsoleView.setEditable(false);
    }
  }


  protected AnAction createRerunAction() {
    return new RestartAction(this);
  }

  private void enableConsoleExecuteAction() {
    myConsoleExecuteActionHandler.setEnabled(true);
  }

  private boolean handshake() {
    boolean res;
    long started = System.currentTimeMillis();
    do {
      try {
        res = myPydevConsoleCommunication.handshake();
      }
      catch (XmlRpcException ignored) {
        res = false;
      }
      if (res) {
        break;
      }
      else {
        long now = System.currentTimeMillis();
        if (now - started > HANDSHAKE_TIMEOUT) {
          break;
        }
        else {
          TimeoutUtil.sleep(100);
        }
      }
    }
    while (true);
    return res;
  }


  private AnAction createStopAction() {
    AnAction generalStopAction = ActionManager.getInstance().getAction(IdeActions.ACTION_STOP_PROGRAM);
    final AnAction stopAction = new DumbAwareAction() {
      @Override
      public void update(AnActionEvent e) {
        generalStopAction.update(e);
      }

      @Override
      public void actionPerformed(AnActionEvent e) {
        e = stopConsole(e);

        generalStopAction.actionPerformed(e);
      }
    };
    stopAction.copyFrom(generalStopAction);
    return stopAction;
  }

  private class SoftWrapAction extends ToggleAction implements DumbAware {
    private boolean isSelected = myConsoleSettings.isUseSoftWraps();

    SoftWrapAction() {
      super(ActionsBundle.actionText("EditorToggleUseSoftWraps"), ActionsBundle.actionDescription("EditorToggleUseSoftWraps"),
            AllIcons.Actions.ToggleSoftWrap);
      updateEditors();
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return isSelected;
    }

    private void updateEditors() {
      myConsoleView.getEditor().getSettings().setUseSoftWraps(isSelected);
      myConsoleView.getConsoleEditor().getSettings().setUseSoftWraps(isSelected);
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      isSelected = state;
      updateEditors();
      myConsoleSettings.setUseSoftWraps(isSelected);
    }
  }

  private AnAction createCloseAction(final RunContentDescriptor descriptor) {
    final AnAction generalCloseAction = new CloseAction(getExecutor(), descriptor, myProject);

    final AnAction stopAction = new DumbAwareAction() {
      @Override
      public void update(AnActionEvent e) {
        generalCloseAction.update(e);
      }

      @Override
      public void actionPerformed(AnActionEvent e) {
        e = stopConsole(e);

        clearContent(descriptor);

        generalCloseAction.actionPerformed(e);
      }
    };
    stopAction.copyFrom(generalCloseAction);
    return stopAction;
  }

  protected void clearContent(RunContentDescriptor descriptor) {
    PythonConsoleToolWindow toolWindow = PythonConsoleToolWindow.getInstance(myProject);
    if (toolWindow != null && toolWindow.getToolWindow() != null) {
      Content content = toolWindow.getToolWindow().getContentManager().findContent(descriptor.getDisplayName());
      assert content != null;
      toolWindow.getToolWindow().getContentManager().removeContent(content, true);
    }
  }

  private AnActionEvent stopConsole(AnActionEvent e) {
    if (myPydevConsoleCommunication != null) {
      e = new AnActionEvent(e.getInputEvent(), e.getDataContext(), e.getPlace(),
                            e.getPresentation(), e.getActionManager(), e.getModifiers());
      try {
        closeCommunication();
        // waiting for REPL communication before destroying process handler
        Thread.sleep(300);
      }
      catch (Exception ignored) {
        // Ignore
      }
    }
    return e;
  }

  protected AnAction createSplitLineAction() {

    class ConsoleSplitLineAction extends EditorAction {

      private static final String CONSOLE_SPLIT_LINE_ACTION_ID = "Console.SplitLine";

      public ConsoleSplitLineAction() {
        super(new EditorWriteActionHandler() {

          private final SplitLineAction mySplitLineAction = new SplitLineAction();

          @Override
          public boolean isEnabled(Editor editor, DataContext dataContext) {
            return mySplitLineAction.getHandler().isEnabled(editor, dataContext);
          }

          @Override
          public void executeWriteAction(Editor editor, @Nullable Caret caret, DataContext dataContext) {
            ((EditorWriteActionHandler)mySplitLineAction.getHandler()).executeWriteAction(editor, caret, dataContext);
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

  @NotNull
  protected PythonConsoleExecuteActionHandler createExecuteActionHandler() {
    myConsoleExecuteActionHandler =
      new PydevConsoleExecuteActionHandler(myConsoleView, myProcessHandler, myPydevConsoleCommunication);
    myConsoleExecuteActionHandler.setEnabled(false);
    new ConsoleHistoryController(myConsoleType.getTypeId(), "", myConsoleView).install();
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

  private void fireConsoleInitializedEvent(LanguageConsoleView consoleView) {
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
    private PydevConsoleRunnerImpl myConsoleRunner;


    private RestartAction(PydevConsoleRunnerImpl runner) {
      copyFrom(ActionManager.getInstance().getAction(IdeActions.ACTION_RERUN));
      getTemplatePresentation().setIcon(AllIcons.Actions.Restart);
      myConsoleRunner = runner;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myConsoleRunner.rerun();
    }
  }

  private void rerun() {
    new Task.Backgroundable(myProject, "Restarting Console", true) {
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

        GuiUtils.invokeLaterIfNeeded(() -> myRerunAction.consume(myConsoleTitle), ModalityState.defaultModalityState());
      }
    }.queue();
  }


  private class ConnectDebuggerAction extends ToggleAction implements DumbAware {
    private boolean mySelected = false;
    private XDebugSession mySession = null;

    public ConnectDebuggerAction() {
      super("Attach Debugger", "Enables tracing of code executed in console", AllIcons.Actions.StartDebugger);
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return mySelected;
    }

    @Override
    public void update(AnActionEvent e) {
      if (mySession != null) {
        e.getPresentation().setEnabled(false);
      }
      else {
        super.update(e);
        e.getPresentation().setEnabled(true);
      }
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      mySelected = state;

      if (mySelected) {
        try {
          mySession = connectToDebugger();
        }
        catch (Exception e1) {
          LOG.error(e1);
          Messages.showErrorDialog("Can't connect to debugger", "Error Connecting Debugger");
        }
      }
      else {
        //TODO: disable debugging
      }
    }
  }


  private static class NewConsoleAction extends AnAction implements DumbAware {
    public NewConsoleAction() {
      super("New Console", "Creates new python console", AllIcons.General.Add);
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(true);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      PydevConsoleRunner runner =
        PythonConsoleRunnerFactory.getInstance().createConsoleRunner(e.getData(CommonDataKeys.PROJECT), e.getData(LangDataKeys.MODULE));
      runner.run();
    }
  }

  private XDebugSession connectToDebugger() throws ExecutionException {
    final ServerSocket serverSocket = PythonCommandLineState.createServerSocket();

    final XDebugSession session = XDebuggerManager.getInstance(myProject).
      startSessionAndShowTab("Python Console Debugger", PythonIcons.Python.Python, null, true, new XDebugProcessStarter() {
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

          myProcessHandler.notifyTextAvailable("\nDebugger connected.\n", ProcessOutputTypes.STDERR);

          return consoleDebugProcess;
        }
      });

    return session;
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

  private static class PythonConsoleRunParams implements PythonRunParams {
    private final PyConsoleOptions.PyConsoleSettings myConsoleSettings;
    private String myWorkingDir;
    private Sdk mySdk;
    private Map<String, String> myEnvironmentVariables;

    public PythonConsoleRunParams(@NotNull PyConsoleOptions.PyConsoleSettings consoleSettings,
                                  @NotNull String workingDir,
                                  @NotNull Sdk sdk,
                                  @NotNull Map<String, String> envs) {
      myConsoleSettings = consoleSettings;
      myWorkingDir = workingDir;
      mySdk = sdk;
      myEnvironmentVariables = envs;
      myEnvironmentVariables.putAll(consoleSettings.getEnvs());
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
