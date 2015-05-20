/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.google.common.base.CharMatcher;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Collections2;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionHelper;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.EncodingEnvironmentUtil;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.console.ConsoleHistoryController;
import com.intellij.execution.console.LanguageConsoleView;
import com.intellij.execution.console.ProcessBackedConsoleExecuteActionHandler;
import com.intellij.execution.process.CommandLineArgumentsProvider;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.runners.AbstractConsoleRunnerWithHistory;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.editor.actions.SplitLineAction;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
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
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.remote.RemoteSshProcess;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.PathMappingSettings;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.net.NetUtils;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugProcessStarter;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.console.completion.PydevConsoleElement;
import com.jetbrains.python.console.parsing.PythonConsoleData;
import com.jetbrains.python.console.pydev.ConsoleCommunication;
import com.jetbrains.python.console.pydev.ConsoleCommunicationListener;
import com.jetbrains.python.debugger.PyDebugRunner;
import com.jetbrains.python.debugger.PySourcePosition;
import com.jetbrains.python.remote.PyRemoteSdkAdditionalDataBase;
import com.jetbrains.python.remote.PyRemoteSdkCredentials;
import com.jetbrains.python.remote.PythonRemoteInterpreterManager;
import com.jetbrains.python.run.ProcessRunner;
import com.jetbrains.python.run.PythonCommandLineState;
import com.jetbrains.python.run.PythonTracebackFilter;
import com.jetbrains.python.sdk.PySdkUtil;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import icons.PythonIcons;
import org.apache.xmlrpc.XmlRpcException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.Charset;
import java.util.*;

import static com.jetbrains.python.sdk.PythonEnvUtil.setPythonIOEncoding;
import static com.jetbrains.python.sdk.PythonEnvUtil.setPythonUnbuffered;

/**
 * @author oleg
 */
public class PydevConsoleRunner extends AbstractConsoleRunnerWithHistory<PythonConsoleView> {
  public static final String WORKING_DIR_ENV = "WORKING_DIR_AND_PYTHON_PATHS";
  public static final String CONSOLE_START_COMMAND = "import sys; print('Python %s on %s' % (sys.version, sys.platform))\n" +
                                                     "sys.path.extend([" + WORKING_DIR_ENV + "])\n";
  private static final Logger LOG = Logger.getInstance(PydevConsoleRunner.class.getName());
  @SuppressWarnings("SpellCheckingInspection")
  public static final String PYDEV_PYDEVCONSOLE_PY = "pydev/pydevconsole.py";
  public static final int PORTS_WAITING_TIMEOUT = 20000;

  private Sdk mySdk;
  private CommandLineArgumentsProvider myCommandLineArgumentsProvider;
  protected int[] myPorts;
  private PydevConsoleCommunication myPydevConsoleCommunication;
  private PyConsoleProcessHandler myProcessHandler;
  protected PydevConsoleExecuteActionHandler myConsoleExecuteActionHandler;
  private List<ConsoleListener> myConsoleListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final PyConsoleType myConsoleType;
  private Map<String, String> myEnvironmentVariables;
  private String myCommandLine;
  private String[] myStatementsToExecute = ArrayUtil.EMPTY_STRING_ARRAY;

  public static Key<ConsoleCommunication> CONSOLE_KEY = new Key<ConsoleCommunication>("PYDEV_CONSOLE_KEY");

  public static Key<Sdk> CONSOLE_SDK = new Key<Sdk>("PYDEV_CONSOLE_SDK_KEY");

  private static final long APPROPRIATE_TO_WAIT = 60000;
  private PyRemoteSdkCredentials myRemoteCredentials;

  private String myConsoleTitle = null;

  public PydevConsoleRunner(@NotNull final Project project,
                            @NotNull Sdk sdk, @NotNull final PyConsoleType consoleType,
                            @Nullable final String workingDir,
                            Map<String, String> environmentVariables, String... statementsToExecute) {
    super(project, consoleType.getTitle(), workingDir);
    mySdk = sdk;
    myConsoleType = consoleType;
    myEnvironmentVariables = environmentVariables;
    myStatementsToExecute = statementsToExecute;
  }

  public static PathMappingSettings getMappings(Project project, Sdk sdk) {
    PathMappingSettings mappingSettings = null;
    if (PySdkUtil.isRemote(sdk)) {
      PythonRemoteInterpreterManager instance = PythonRemoteInterpreterManager.getInstance();
      if (instance != null) {
        //noinspection ConstantConditions
        mappingSettings =
          instance.setupMappings(project, (PyRemoteSdkAdditionalDataBase)sdk.getSdkAdditionalData(), null);
      }
    }
    return mappingSettings;
  }

  @NotNull
  public static Pair<Sdk, Module> findPythonSdkAndModule(@NotNull Project project, @Nullable Module contextModule) {
    Sdk sdk = null;
    Module module = null;
    PyConsoleOptions.PyConsoleSettings settings = PyConsoleOptions.getInstance(project).getPythonConsoleSettings();
    String sdkHome = settings.getSdkHome();
    if (sdkHome != null) {
      sdk = PythonSdkType.findSdkByPath(sdkHome);
      if (settings.getModuleName() != null) {
        module = ModuleManager.getInstance(project).findModuleByName(settings.getModuleName());
      }
      else {
        module = contextModule;
        if (module == null && ModuleManager.getInstance(project).getModules().length > 0) {
          module = ModuleManager.getInstance(project).getModules()[0];
        }
      }
    }
    if (sdk == null && settings.isUseModuleSdk()) {
      if (contextModule != null) {
        module = contextModule;
      }
      else if (settings.getModuleName() != null) {
        module = ModuleManager.getInstance(project).findModuleByName(settings.getModuleName());
      }
      if (module != null) {
        if (PythonSdkType.findPythonSdk(module) != null) {
          sdk = PythonSdkType.findPythonSdk(module);
        }
      }
    }
    else if (contextModule != null) {
      if (module == null) {
        module = contextModule;
      }
      if (sdk == null) {
        sdk = PythonSdkType.findPythonSdk(module);
      }
    }

    if (sdk == null) {
      for (Module m : ModuleManager.getInstance(project).getModules()) {
        if (PythonSdkType.findPythonSdk(m) != null) {
          sdk = PythonSdkType.findPythonSdk(m);
          module = m;
          break;
        }
      }
    }
    if (sdk == null) {
      if (PythonSdkType.getAllSdks().size() > 0) {
        //noinspection UnusedAssignment
        sdk = PythonSdkType.getAllSdks().get(0); //take any python sdk
      }
    }
    return Pair.create(sdk, module);
  }

  public static String constructPythonPathCommand(Collection<String> pythonPath, String command) {
    final String path = Joiner.on(", ").join(Collections2.transform(pythonPath, new Function<String, String>() {
      @Override
      public String apply(String input) {
        return "'" + input.replace("\\", "\\\\").replace("'", "\\'") + "'";
      }
    }));

    return command.replace(WORKING_DIR_ENV, path);
  }

  public static Map<String, String> addDefaultEnvironments(Sdk sdk, Map<String, String> envs, @NotNull Project project) {
    setCorrectStdOutEncoding(envs, project);

    PythonSdkFlavor.initPythonPath(envs, true, PythonCommandLineState.getAddedPaths(sdk));
    return envs;
  }

  /**
   * Add requered ENV var to Python task to set its stdout charset to current project charset to allow it print correctly.
   * @param envs map of envs to add variable
   * @param project current project
   */
  public static void setCorrectStdOutEncoding(@NotNull final Map<String, String> envs, @NotNull final Project project) {
    final Charset defaultCharset = EncodingProjectManager.getInstance(project).getDefaultCharset();

    final String encoding = defaultCharset.name();
    setPythonIOEncoding(setPythonUnbuffered(envs), encoding);
  }


  @Override
  protected List<AnAction> fillToolBarActions(final DefaultActionGroup toolbarActions,
                                              final Executor defaultExecutor,
                                              final RunContentDescriptor contentDescriptor) {
    AnAction backspaceHandlingAction = createBackspaceHandlingAction();
    //toolbarActions.add(backspaceHandlingAction);
    AnAction interruptAction = createInterruptAction();

    AnAction rerunAction = createRerunAction();
    toolbarActions.add(rerunAction);

    List<AnAction> actions = super.fillToolBarActions(toolbarActions, defaultExecutor, contentDescriptor);

    actions.add(0, rerunAction);

    actions.add(backspaceHandlingAction);
    actions.add(interruptAction);

    actions.add(createSplitLineAction());

    AnAction showVarsAction = new ShowVarsAction();
    toolbarActions.add(showVarsAction);
    toolbarActions.add(ConsoleHistoryController.getController(getConsoleView()).getBrowseHistory());

    toolbarActions.add(new ConnectDebuggerAction());

    toolbarActions.add(new NewConsoleAction());

    return actions;
  }

  public void runSync() {
    myPorts = findAvailablePorts(getProject(), myConsoleType);

    assert myPorts != null;

    myCommandLineArgumentsProvider = createCommandLineArgumentsProvider(mySdk, myEnvironmentVariables, myPorts);

    try {
      super.initAndRun();
    }
    catch (ExecutionException e) {
      LOG.warn("Error running console", e);
      ExecutionHelper.showErrors(getProject(), Arrays.<Exception>asList(e), "Python Console", null);
    }

    ProgressManager.getInstance().run(new Task.Backgroundable(getProject(), "Connecting to console", false) {
      @Override
      public void run(@NotNull final ProgressIndicator indicator) {
        indicator.setText("Connecting to console...");
        connect(myStatementsToExecute);
      }
    });
  }

  /**
   * Opens console
   */
  public void open() {
    run();
  }


  /**
   * Creates new console tab
   */
  public void createNewConsole() {
    run();
  }

  public void run() {
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        FileDocumentManager.getInstance().saveAllDocuments();
      }
    });

    myPorts = findAvailablePorts(getProject(), myConsoleType);

    assert myPorts != null;

    myCommandLineArgumentsProvider = createCommandLineArgumentsProvider(mySdk, myEnvironmentVariables, myPorts);

    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        ProgressManager.getInstance().run(new Task.Backgroundable(getProject(), "Connecting to console", false) {
          @Override
          public void run(@NotNull final ProgressIndicator indicator) {
            indicator.setText("Connecting to console...");
            try {
              initAndRun(myStatementsToExecute);
            }
            catch (ExecutionException e) {
              LOG.warn("Error running console", e);
              assert myProject != null;
              ExecutionHelper.showErrors(myProject, Arrays.<Exception>asList(e), getTitle(), null);
            }
          }
        });
      }
    });
  }

  private static int[] findAvailablePorts(Project project, PyConsoleType consoleType) {
    final int[] ports;
    try {
      // File "pydev/console/pydevconsole.py", line 223, in <module>
      // port, client_port = sys.argv[1:3]
      ports = NetUtils.findAvailableSocketPorts(2);
    }
    catch (IOException e) {
      ExecutionHelper.showErrors(project, Arrays.<Exception>asList(e), consoleType.getTitle(), null);
      return null;
    }
    return ports;
  }

  protected CommandLineArgumentsProvider createCommandLineArgumentsProvider(final Sdk sdk,
                                                                            final Map<String, String> environmentVariables,
                                                                            int[] ports) {
    final ArrayList<String> args = new ArrayList<String>();
    args.add(sdk.getHomePath());
    final String versionString = sdk.getVersionString();
    if (versionString == null || !versionString.toLowerCase().contains("jython")) {
      args.add("-u");
    }
    args.add(FileUtil.toSystemDependentName(PythonHelpersLocator.getHelperPath(PYDEV_PYDEVCONSOLE_PY)));
    for (int port : ports) {
      args.add(String.valueOf(port));
    }
    return new CommandLineArgumentsProvider() {
      @Override
      public String[] getArguments() {
        return ArrayUtil.toStringArray(args);
      }

      @Override
      public boolean passParentEnvs() {
        return false;
      }

      @Override
      public Map<String, String> getAdditionalEnvs() {
        return addDefaultEnvironments(sdk, environmentVariables, getProject());
      }
    };
  }

  @Override
  protected PythonConsoleView createConsoleView() {
    PythonConsoleView consoleView = new PythonConsoleView(getProject(), getConsoleTitle(), mySdk);
    myPydevConsoleCommunication.setConsoleFile(consoleView.getVirtualFile());
    consoleView.addMessageFilter(new PythonTracebackFilter(getProject()));
    return consoleView;
  }

  @Override
  protected Process createProcess() throws ExecutionException {
    if (PySdkUtil.isRemote(mySdk)) {
      PythonRemoteInterpreterManager manager = PythonRemoteInterpreterManager.getInstance();
      if (manager != null) {
        return createRemoteConsoleProcess(manager, myCommandLineArgumentsProvider.getArguments(),
                                          myCommandLineArgumentsProvider.getAdditionalEnvs());
      }
      throw new PythonRemoteInterpreterManager.PyRemoteInterpreterExecutionException();
    }
    else {
      myCommandLine = myCommandLineArgumentsProvider.getCommandLineString();
      Map<String, String> envs = myCommandLineArgumentsProvider.getAdditionalEnvs();
      if (envs != null) {
        EncodingEnvironmentUtil.fixDefaultEncodingIfMac(envs, getProject());
      }
      final Process server = ProcessRunner
        .createProcess(getWorkingDir(), envs, myCommandLineArgumentsProvider.getArguments());
      try {
        myPydevConsoleCommunication = new PydevConsoleCommunication(getProject(), myPorts[0], server, myPorts[1]);
      }
      catch (Exception e) {
        throw new ExecutionException(e.getMessage());
      }
      return server;
    }
  }

  private Process createRemoteConsoleProcess(PythonRemoteInterpreterManager manager, String[] command, Map<String, String> env)
    throws ExecutionException {
    PyRemoteSdkAdditionalDataBase data = (PyRemoteSdkAdditionalDataBase)mySdk.getSdkAdditionalData();
    assert data != null;

    GeneralCommandLine commandLine = new GeneralCommandLine(command);


    commandLine.getEnvironment().putAll(env);

    commandLine.getParametersList().set(1, PythonRemoteInterpreterManager.toSystemDependent(new File(data.getHelpersPath(),
                                                                                                     PYDEV_PYDEVCONSOLE_PY)
                                                                                              .getPath(),
                                                                                            PySourcePosition.isWindowsPath(
                                                                                              data.getInterpreterPath())
    ));
    commandLine.getParametersList().set(2, "0");
    commandLine.getParametersList().set(3, "0");

    myCommandLine = commandLine.getCommandLineString();

    try {
      myRemoteCredentials = data.getRemoteSdkCredentials(true);
      PathMappingSettings mappings = manager.setupMappings(getProject(), data, null);

      RemoteSshProcess remoteProcess =
        manager.createRemoteProcess(getProject(), myRemoteCredentials, mappings, commandLine, true);


      Couple<Integer> remotePorts = getRemotePortsFromProcess(remoteProcess);

      remoteProcess.addLocalTunnel(myPorts[0], myRemoteCredentials.getHost(), remotePorts.first);
      remoteProcess.addRemoteTunnel(remotePorts.second, "localhost", myPorts[1]);


      myPydevConsoleCommunication = new PydevRemoteConsoleCommunication(getProject(), myPorts[0], remoteProcess, myPorts[1]);
      return remoteProcess;
    }
    catch (Exception e) {
      throw new ExecutionException(e.getMessage());
    }
  }

  private static Couple<Integer> getRemotePortsFromProcess(RemoteSshProcess process) throws ExecutionException {
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

  @Override
  protected PyConsoleProcessHandler createProcessHandler(final Process process) {
    if (PySdkUtil.isRemote(mySdk)) {
      PythonRemoteInterpreterManager manager = PythonRemoteInterpreterManager.getInstance();
      if (manager != null) {
        PyRemoteSdkAdditionalDataBase data = (PyRemoteSdkAdditionalDataBase)mySdk.getSdkAdditionalData();
        assert data != null;
        myProcessHandler =
          manager.createConsoleProcessHandler(process, myRemoteCredentials, getConsoleView(), myPydevConsoleCommunication,
                                              myCommandLine, CharsetToolkit.UTF8_CHARSET,
                                              manager.setupMappings(getProject(), data, null));
      }
      else {
        LOG.error("Can't create remote console process handler");
      }
    }
    else {
      myProcessHandler = new PyConsoleProcessHandler(process, getConsoleView(), myPydevConsoleCommunication, myCommandLine,
                                                     CharsetToolkit.UTF8_CHARSET);
    }
    return myProcessHandler;
  }

  public void initAndRun(final String... statements2execute) throws ExecutionException {
    super.initAndRun();

    connect(statements2execute);
  }

  public void connect(final String[] statements2execute) {
    if (handshake()) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {

        @Override
        public void run() {
          // Propagate console communication to language console
          final PythonConsoleView consoleView = getConsoleView();

          consoleView.setConsoleCommunication(myPydevConsoleCommunication);
          consoleView.setSdk(mySdk);
          consoleView.setExecutionHandler(myConsoleExecuteActionHandler);
          myProcessHandler.addProcessListener(new ProcessAdapter() {
            @Override
            public void onTextAvailable(ProcessEvent event, Key outputType) {
              consoleView.print(event.getText(), outputType);
            }
          });

          enableConsoleExecuteAction();

          for (String statement : statements2execute) {
            consoleView.executeStatement(statement + "\n", ProcessOutputTypes.SYSTEM);
          }

          fireConsoleInitializedEvent(consoleView);
        }
      });
    }
    else {
      getConsoleView().print("Couldn't connect to console process.", ProcessOutputTypes.STDERR);
      myProcessHandler.destroyProcess();
      finishConsole();
    }
  }

  @Override
  protected String constructConsoleTitle(@NotNull String consoleTitle) {
    if (myConsoleTitle == null) {
      myConsoleTitle = super.constructConsoleTitle(consoleTitle);
    }
    return myConsoleTitle;
  }

  protected AnAction createRerunAction() {
    return new RestartAction(this);
  }

  private AnAction createInterruptAction() {
    AnAction anAction = new AnAction() {
      @Override
      public void actionPerformed(final AnActionEvent e) {
        if (myPydevConsoleCommunication.isExecuting()) {
          getConsoleView().print("^C", ProcessOutputTypes.SYSTEM);
        }
        myPydevConsoleCommunication.interrupt();
      }

      @Override
      public void update(final AnActionEvent e) {
        EditorEx consoleEditor = getConsoleView().getConsoleEditor();
        boolean enabled = IJSwingUtilities.hasFocus(consoleEditor.getComponent()) && !consoleEditor.getSelectionModel().hasSelection();
        e.getPresentation().setEnabled(enabled);
      }
    };
    anAction
      .registerCustomShortcutSet(KeyEvent.VK_C, InputEvent.CTRL_MASK, getConsoleView().getConsoleEditor().getComponent());
    anAction.getTemplatePresentation().setVisible(false);
    return anAction;
  }


  private AnAction createBackspaceHandlingAction() {
    final AnAction upAction = new AnAction() {
      @Override
      public void actionPerformed(final AnActionEvent e) {
        new WriteCommandAction(getConsoleView().getProject(), getConsoleView().getFile()) {
          @Override
          protected void run(@NotNull final Result result) throws Throwable {
            String text = getConsoleView().getEditorDocument().getText();
            String newText = text.substring(0, text.length() - myConsoleExecuteActionHandler.getPythonIndent());
            getConsoleView().getEditorDocument().setText(newText);
            getConsoleView().getConsoleEditor().getCaretModel().moveToOffset(newText.length());
          }
        }.execute();
      }

      @Override
      public void update(final AnActionEvent e) {
        e.getPresentation()
          .setEnabled(myConsoleExecuteActionHandler.getCurrentIndentSize() >= myConsoleExecuteActionHandler.getPythonIndent() &&
                      isIndentSubstring(getConsoleView().getEditorDocument().getText()));
      }
    };
    upAction.registerCustomShortcutSet(KeyEvent.VK_BACK_SPACE, 0, null);
    upAction.getTemplatePresentation().setVisible(false);
    return upAction;
  }

  private boolean isIndentSubstring(String text) {
    int indentSize = myConsoleExecuteActionHandler.getPythonIndent();
    return text.length() >= indentSize && CharMatcher.WHITESPACE.matchesAllOf(text.substring(text.length() - indentSize));
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
        if (now - started > APPROPRIATE_TO_WAIT) {
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

  @Override
  protected AnAction createStopAction() {
    final AnAction generalStopAction = super.createStopAction();
    return createConsoleStoppingAction(generalStopAction);
  }

  @Override
  protected AnAction createCloseAction(Executor defaultExecutor, final RunContentDescriptor descriptor) {
    final AnAction generalCloseAction = super.createCloseAction(defaultExecutor, descriptor);

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
  }

  private AnAction createConsoleStoppingAction(final AnAction generalStopAction) {
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
  @Override
  protected ProcessBackedConsoleExecuteActionHandler createExecuteActionHandler() {
    myConsoleExecuteActionHandler =
      new PydevConsoleExecuteActionHandler(getConsoleView(), getProcessHandler(), myPydevConsoleCommunication);
    myConsoleExecuteActionHandler.setEnabled(false);
    new ConsoleHistoryController(myConsoleType.getTypeId(), "", getConsoleView()).install();
    return myConsoleExecuteActionHandler;
  }

  public PydevConsoleCommunication getPydevConsoleCommunication() {
    return myPydevConsoleCommunication;
  }

  public static boolean isInPydevConsole(final PsiElement element) {
    return element instanceof PydevConsoleElement || getConsoleCommunication(element) != null;
  }

  public static boolean isPythonConsole(@Nullable FileElement element) {
    return getPythonConsoleData(element) != null;
  }

  @Nullable
  public static PythonConsoleData getPythonConsoleData(@Nullable FileElement element) {
    if (element == null || element.getPsi() == null || element.getPsi().getContainingFile() == null) {
      return null;
    }

    VirtualFile file = getConsoleFile(element.getPsi().getContainingFile());

    if (file == null) {
      return null;
    }
    return file.getUserData(PyConsoleUtil.PYTHON_CONSOLE_DATA);
  }

  private static VirtualFile getConsoleFile(PsiFile psiFile) {
    VirtualFile file = psiFile.getViewProvider().getVirtualFile();
    if (file instanceof LightVirtualFile) {
      file = ((LightVirtualFile)file).getOriginalFile();
    }
    return file;
  }

  @Nullable
  public static ConsoleCommunication getConsoleCommunication(final PsiElement element) {
    final PsiFile containingFile = element.getContainingFile();
    return containingFile != null ? containingFile.getCopyableUserData(CONSOLE_KEY) : null;
  }

  @Nullable
  public static Sdk getConsoleSdk(final PsiElement element) {
    final PsiFile containingFile = element.getContainingFile();
    return containingFile != null ? containingFile.getCopyableUserData(CONSOLE_SDK) : null;
  }

  @Override
  protected boolean shouldAddNumberToTitle() {
    return true;
  }

  public void addConsoleListener(ConsoleListener consoleListener) {
    myConsoleListeners.add(consoleListener);
  }

  public void removeConsoleListener(ConsoleListener consoleListener) {
    myConsoleListeners.remove(consoleListener);
  }

  private void fireConsoleInitializedEvent(LanguageConsoleView consoleView) {
    for (ConsoleListener listener : myConsoleListeners) {
      listener.handleConsoleInitialized(consoleView);
    }
  }


  public interface ConsoleListener {
    void handleConsoleInitialized(LanguageConsoleView consoleView);
  }


  private static class RestartAction extends AnAction {
    private PydevConsoleRunner myConsoleRunner;


    private RestartAction(PydevConsoleRunner runner) {
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
    new Task.Backgroundable(getProject(), "Restarting console", true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        UIUtil.invokeLaterIfNeeded(new Runnable() {
          @Override
          public void run() {
            closeCommunication();
          }
        });

        myProcessHandler.waitFor();

        UIUtil.invokeLaterIfNeeded(new Runnable() {
          @Override
          public void run() {
            PydevConsoleRunner.this.run();
          }
        });
      }
    }.queue();
  }

  private class ShowVarsAction extends ToggleAction implements DumbAware {
    private boolean mySelected = false;

    public ShowVarsAction() {
      super("Show Variables", "Shows active console variables", AllIcons.Debugger.Watches);
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return mySelected;
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      mySelected = state;

      if (mySelected) {
        getConsoleView().showVariables(myPydevConsoleCommunication);
      }
      else {
        getConsoleView().restoreWindow();
      }
    }
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
      PydevConsoleRunner runner = PythonConsoleRunnerFactory.getInstance().createConsoleRunner(e.getData(CommonDataKeys.PROJECT), e.getData(LangDataKeys.MODULE));
      runner.createNewConsole();
    }
  }

  private XDebugSession connectToDebugger() throws ExecutionException {
    final ServerSocket serverSocket = PythonCommandLineState.createServerSocket();

    final XDebugSession session = XDebuggerManager.getInstance(getProject()).
      startSessionAndShowTab("Python Console Debugger", PythonIcons.Python.Python, null, true, new XDebugProcessStarter() {
        @NotNull
        public XDebugProcess start(@NotNull final XDebugSession session) {
          PythonDebugLanguageConsoleView debugConsoleView = new PythonDebugLanguageConsoleView(getProject(), mySdk);

          PyConsoleDebugProcessHandler consoleDebugProcessHandler =
            new PyConsoleDebugProcessHandler(myProcessHandler);

          PyConsoleDebugProcess consoleDebugProcess =
            new PyConsoleDebugProcess(session, serverSocket, debugConsoleView,
                                      consoleDebugProcessHandler);

          PythonDebugConsoleCommunication communication =
            PyDebugRunner.initDebugConsoleView(getProject(), consoleDebugProcess, debugConsoleView, consoleDebugProcessHandler, session);

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

  public static PythonConsoleRunnerFactory factory() {
    return new PydevConsoleRunnerFactory();
  }
}
