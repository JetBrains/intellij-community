/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.google.common.collect.Maps;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionHelper;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.console.ConsoleHistoryController;
import com.intellij.execution.console.LanguageConsoleView;
import com.intellij.execution.process.CommandLineArgumentsProvider;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.runners.AbstractConsoleRunnerWithHistory;
import com.intellij.execution.runners.ConsoleExecuteActionHandler;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.remotesdk.RemoteSdkData;
import com.intellij.remotesdk.RemoteSshProcess;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.net.NetUtils;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.impl.frame.XStandaloneVariablesView;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.console.completion.PydevConsoleElement;
import com.jetbrains.python.console.parsing.PythonConsoleData;
import com.jetbrains.python.console.pydev.ConsoleCommunication;
import com.jetbrains.python.console.pydev.ConsoleCommunicationListener;
import com.jetbrains.python.debugger.PyDebuggerEditorsProvider;
import com.jetbrains.python.debugger.PySourcePosition;
import com.jetbrains.python.debugger.PyStackFrame;
import com.jetbrains.python.debugger.PyStackFrameInfo;
import com.jetbrains.python.remote.PythonRemoteInterpreterManager;
import com.jetbrains.python.run.ProcessRunner;
import com.jetbrains.python.run.PythonCommandLineState;
import com.jetbrains.python.run.PythonTracebackFilter;
import com.jetbrains.python.sdk.PySdkUtil;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import org.apache.xmlrpc.XmlRpcException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;

import static com.jetbrains.python.sdk.PythonEnvUtil.setPythonIOEncoding;
import static com.jetbrains.python.sdk.PythonEnvUtil.setPythonUnbuffered;

/**
 * @author oleg
 */
public class PydevConsoleRunner extends AbstractConsoleRunnerWithHistory<PythonConsoleView> {
  private static final Logger LOG = Logger.getInstance(PydevConsoleRunner.class.getName());
  public static final String PYDEV_PYDEVCONSOLE_PY = "pydev/pydevconsole.py";
  public static final int PORTS_WAITING_TIMEOUT = 20000;

  private Sdk mySdk;
  @NotNull private CommandLineArgumentsProvider myCommandLineArgumentsProvider;
  private int[] myPorts;
  private PydevConsoleCommunication myPydevConsoleCommunication;
  private PyConsoleProcessHandler myProcessHandler;
  private PydevConsoleExecuteActionHandler myConsoleExecuteActionHandler;
  private List<ConsoleListener> myConsoleListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final PyConsoleType myConsoleType;
  private Map<String, String> myEnvironmentVariables;
  private String myCommandLine;
  private String[] myStatementsToExecute = ArrayUtil.EMPTY_STRING_ARRAY;
  private ConsoleHistoryController myHistoryController;

  public static Key<ConsoleCommunication> CONSOLE_KEY = new Key<ConsoleCommunication>("PYDEV_CONSOLE_KEY");

  public static Key<Sdk> CONSOLE_SDK = new Key<Sdk>("PYDEV_CONSOLE_SDK_KEY");

  private static final String PYTHON_ENV_COMMAND = "import sys; print('Python %s on %s' % (sys.version, sys.platform))\n";

  private static final long APPROPRIATE_TO_WAIT = 60000;

  protected PydevConsoleRunner(@NotNull final Project project,
                               @NotNull Sdk sdk, @NotNull final PyConsoleType consoleType,
                               @Nullable final String workingDir,
                               Map<String, String> environmentVariables) {
    super(project, consoleType.getTitle(), workingDir);
    mySdk = sdk;
    myConsoleType = consoleType;
    myEnvironmentVariables = environmentVariables;
  }

  public void setStatementsToExecute(String... statementsToExecute) {
    myStatementsToExecute = statementsToExecute;
  }

  public static Map<String, String> addDefaultEnvironments(Sdk sdk, Map<String, String> envs) {
    setPythonIOEncoding(setPythonUnbuffered(envs), "utf-8");
    PythonSdkFlavor.initPythonPath(envs, true, PythonCommandLineState.getAddedPaths(sdk));
    return envs;
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

    AnAction showVarsAction = new ShowVarsAction();
    toolbarActions.add(showVarsAction);
    toolbarActions.add(myHistoryController.getBrowseHistory());
    return actions;
  }

  @NotNull
  public static PydevConsoleRunner createAndRun(@NotNull final Project project,
                                                @NotNull final Sdk sdk,
                                                @NotNull final PyConsoleType consoleType,
                                                @Nullable final String workingDirectory,
                                                @NotNull final Map<String, String> environmentVariables,
                                                final String... statements2execute) {
    final PydevConsoleRunner consoleRunner = create(project, sdk, consoleType, workingDirectory, environmentVariables);
    consoleRunner.setStatementsToExecute(statements2execute);
    consoleRunner.run();
    return consoleRunner;
  }

  public void run() {
    myPorts = findAvailablePorts(getProject(), myConsoleType);

    assert myPorts != null;

    myCommandLineArgumentsProvider = createCommandLineArgumentsProvider(mySdk, myEnvironmentVariables, myPorts);

    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        ProgressManager.getInstance().run(new Task.Backgroundable(getProject(), "Connecting to console", false) {
          public void run(@NotNull final ProgressIndicator indicator) {
            indicator.setText("Connecting to console...");
            try {
              initAndRun(myStatementsToExecute);
            }
            catch (ExecutionException e) {
              LOG.warn("Error running console", e);
              ExecutionHelper.showErrors(myProject, Arrays.<Exception>asList(e), getTitle(), null);
            }
          }
        });
      }
    });
  }

  public static PydevConsoleRunner create(Project project,
                                          Sdk sdk,
                                          PyConsoleType consoleType,
                                          String workingDirectory) {
    return create(project, sdk, consoleType, workingDirectory, Maps.<String, String>newHashMap());
  }

  @NotNull
  private static PydevConsoleRunner create(@NotNull final Project project,
                                           @NotNull final Sdk sdk,
                                           @NotNull final PyConsoleType consoleType,
                                           @Nullable final String workingDirectory,
                                           @NotNull final Map<String, String> environmentVariables) {
    return new PydevConsoleRunner(project, sdk, consoleType, workingDirectory, environmentVariables);
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

  private static CommandLineArgumentsProvider createCommandLineArgumentsProvider(final Sdk sdk,
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
      public String[] getArguments() {
        return ArrayUtil.toStringArray(args);
      }

      public boolean passParentEnvs() {
        return false;
      }

      public Map<String, String> getAdditionalEnvs() {
        return addDefaultEnvironments(sdk, environmentVariables);
      }
    };
  }

  @Override
  protected PythonConsoleView createConsoleView() {
    PythonConsoleView consoleView = new PythonConsoleView(getProject(), getConsoleTitle(), mySdk);
    myPydevConsoleCommunication.setConsoleFile(consoleView.getConsoleVirtualFile());
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
      final Process server = ProcessRunner
        .createProcess(getWorkingDir(), myCommandLineArgumentsProvider.getAdditionalEnvs(), myCommandLineArgumentsProvider.getArguments());
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
    RemoteSdkData data = (RemoteSdkData)mySdk.getSdkAdditionalData();

    GeneralCommandLine commandLine = new GeneralCommandLine(command);
    commandLine.getEnvironment().putAll(env);

    commandLine.getParametersList().set(1, PythonRemoteInterpreterManager.toSystemDependent(new File(data.getHelpersPath(),
                                                                                                     PYDEV_PYDEVCONSOLE_PY)
                                                                                              .getPath(),
                                                                                            PySourcePosition.isWindowsPath(
                                                                                              data.getInterpreterPath())));
    commandLine.getParametersList().set(2, "0");
    commandLine.getParametersList().set(3, "0");

    myCommandLine = commandLine.getCommandLineString();


    RemoteSshProcess remoteProcess =
      manager.createRemoteProcess(getProject(), data, commandLine, true);


    Pair<Integer, Integer> remotePorts = getRemotePortsFromProcess(remoteProcess);

    remoteProcess.addLocalTunnel(myPorts[0], data.getHost(), remotePorts.first);
    remoteProcess.addRemoteTunnel(remotePorts.second, "localhost", myPorts[1]);


    try {
      myPydevConsoleCommunication = new PydevConsoleCommunication(getProject(), myPorts[0], remoteProcess, myPorts[1]);
      return remoteProcess;
    }
    catch (Exception e) {
      throw new ExecutionException(e.getMessage());
    }
  }

  private static Pair<Integer, Integer> getRemotePortsFromProcess(RemoteSshProcess process) throws ExecutionException {
    Scanner s = new Scanner(process.getInputStream());

    return Pair.create(readInt(s, process), readInt(s, process));
  }

  private static int readInt(Scanner s, Process process) throws ExecutionException {
    long started = System.currentTimeMillis();

    while (System.currentTimeMillis() - started < PORTS_WAITING_TIMEOUT) {
      if (s.hasNextLine()) {
        String line = s.nextLine();
        try {
          return Integer.parseInt(line);
        }
        catch (NumberFormatException e) {
          continue;
        }
      }

      try {

        Thread.sleep(200);
      }
      catch (InterruptedException e1) {
      }

      if (process.exitValue() != 0) {
        String error;
        try {
          error = "Console process terminated with error:\n" + StreamUtil.readText(process.getErrorStream());
        }
        catch (Exception e) {
          error = "Console process terminated with exit code " + process.exitValue();
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
    myProcessHandler = new PyConsoleProcessHandler(process, getConsoleView(), myPydevConsoleCommunication, myCommandLine,
                                                   CharsetToolkit.UTF8_CHARSET);
    return myProcessHandler;
  }

  public void initAndRun(final String... statements2execute) throws ExecutionException {
    super.initAndRun();

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

          consoleView.executeStatement(PYTHON_ENV_COMMAND, ProcessOutputTypes.SYSTEM);

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
        EditorEx consoleEditor = getConsoleView().getConsole().getConsoleEditor();
        boolean enabled = IJSwingUtilities.hasFocus(consoleEditor.getComponent()) && !consoleEditor.getSelectionModel().hasSelection();
        e.getPresentation().setEnabled(enabled);
      }
    };
    anAction.registerCustomShortcutSet(KeyEvent.VK_C, KeyEvent.CTRL_MASK, getConsoleView().getConsole().getConsoleEditor().getComponent());
    anAction.getTemplatePresentation().setVisible(false);
    return anAction;
  }


  private AnAction createBackspaceHandlingAction() {
    final AnAction upAction = new AnAction() {
      @Override
      public void actionPerformed(final AnActionEvent e) {
        new WriteCommandAction(getLanguageConsole().getProject(), getLanguageConsole().getFile()) {
          protected void run(final Result result) throws Throwable {
            String text = getLanguageConsole().getEditorDocument().getText();
            String newText = text.substring(0, text.length() - myConsoleExecuteActionHandler.getPythonIndent());
            getLanguageConsole().getEditorDocument().setText(newText);
            getLanguageConsole().getConsoleEditor().getCaretModel().moveToOffset(newText.length());
          }
        }.execute();
      }

      @Override
      public void update(final AnActionEvent e) {
        e.getPresentation()
          .setEnabled(myConsoleExecuteActionHandler.getCurrentIndentSize() >= myConsoleExecuteActionHandler.getPythonIndent() &&
                      isIndentSubstring(getLanguageConsole().getEditorDocument().getText()));
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
      catch (XmlRpcException e) {
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
          try {
            Thread.sleep(100);
          }
          catch (InterruptedException e) {
          }
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
  protected AnAction createCloseAction(Executor defaultExecutor, RunContentDescriptor myDescriptor) {
    final AnAction generalCloseAction = super.createCloseAction(defaultExecutor, myDescriptor);
    return createConsoleStoppingAction(generalCloseAction);
  }

  private AnAction createConsoleStoppingAction(final AnAction generalStopAction) {
    final AnAction stopAction = new DumbAwareAction() {
      @Override
      public void update(AnActionEvent e) {
        generalStopAction.update(e);
      }

      @Override
      public void actionPerformed(AnActionEvent e) {
        if (myPydevConsoleCommunication != null) {
          final AnActionEvent furtherActionEvent =
            new AnActionEvent(e.getInputEvent(), e.getDataContext(), e.getPlace(),
                              e.getPresentation(), e.getActionManager(), e.getModifiers());
          try {
            closeCommunication();
            // waiting for REPL communication before destroying process handler
            Thread.sleep(300);
          }
          catch (Exception e1) {
            // Ignore
          }
          generalStopAction.actionPerformed(furtherActionEvent);
        }
      }
    };
    stopAction.copyFrom(generalStopAction);
    return stopAction;
  }

  private void closeCommunication() {
    if (!myProcessHandler.isProcessTerminated()) {
      myPydevConsoleCommunication.close();
    }
  }

  @NotNull
  @Override
  protected ConsoleExecuteActionHandler createConsoleExecuteActionHandler() {
    myConsoleExecuteActionHandler =
      new PydevConsoleExecuteActionHandler(getConsoleView(), getProcessHandler(), myPydevConsoleCommunication);
    myConsoleExecuteActionHandler.setEnabled(false);
    myHistoryController = new ConsoleHistoryController(myConsoleType.getTypeId(), "", getLanguageConsole(),
                                 myConsoleExecuteActionHandler.getConsoleHistoryModel());
    myHistoryController.install();
    return myConsoleExecuteActionHandler;
  }

  public PydevConsoleCommunication getPydevConsoleCommunication() {
    return myPydevConsoleCommunication;
  }

  public static boolean isInPydevConsole(final PsiElement element) {
    return element instanceof PydevConsoleElement || getConsoleCommunication(element) != null;
  }

  public static boolean isInPydevConsole(final VirtualFile file) {
    return file.getName().contains("Python Console");
  }

  public static boolean isPythonConsole(@Nullable final FileElement element) {
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
          public void run() {
            PydevConsoleRunner.this.closeCommunication();
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
}
