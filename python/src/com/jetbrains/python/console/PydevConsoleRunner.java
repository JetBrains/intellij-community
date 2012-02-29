package com.jetbrains.python.console;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionHelper;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.console.ConsoleHistoryController;
import com.intellij.execution.console.LanguageConsoleViewImpl;
import com.intellij.execution.process.*;
import com.intellij.execution.runners.AbstractConsoleRunnerWithHistory;
import com.intellij.execution.runners.ConsoleExecuteActionHandler;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.net.NetUtils;
import com.jetbrains.django.run.Runner;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.console.completion.PydevConsoleElement;
import com.jetbrains.python.console.parsing.PythonConsoleData;
import com.jetbrains.python.console.pydev.ConsoleCommunication;
import com.jetbrains.python.debugger.PySourcePosition;
import com.jetbrains.python.remote.PyRemoteInterpreterException;
import com.jetbrains.python.remote.PyRemoteSshProcess;
import com.jetbrains.python.remote.PythonRemoteInterpreterManager;
import com.jetbrains.python.remote.PythonRemoteSdkAdditionalData;
import com.jetbrains.python.run.PythonCommandLineState;
import com.jetbrains.python.run.PythonTracebackFilter;
import com.jetbrains.python.sdk.PythonSdkFlavor;
import org.apache.commons.lang.StringUtils;
import org.apache.xmlrpc.XmlRpcException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.util.*;

import static com.jetbrains.python.sdk.PythonEnvUtil.setPythonIOEncoding;
import static com.jetbrains.python.sdk.PythonEnvUtil.setPythonUnbuffered;

/**
 * @author oleg
 */
public class PydevConsoleRunner extends AbstractConsoleRunnerWithHistory<PythonConsoleView> {
  private static final Logger LOG = Logger.getInstance(PydevConsoleRunner.class.getName());
  public static final String PYDEV_PYDEVCONSOLE_PY = "pydev/pydevconsole.py";

  private Sdk mySdk;
  @NotNull private final CommandLineArgumentsProvider myCommandLineArgumentsProvider;
  private final int[] myPorts;
  private PydevConsoleCommunication myPydevConsoleCommunication;
  private PyConsoleProcessHandler myProcessHandler;
  private PydevConsoleExecuteActionHandler myConsoleExecuteActionHandler;
  private List<ConsoleListener> myConsoleListeners = Lists.newArrayList();
  private final PyConsoleType myConsoleType;
  private String myCommandLine;

  public static Key<ConsoleCommunication> CONSOLE_KEY = new Key<ConsoleCommunication>("PYDEV_CONSOLE_KEY");

  private static final String PYTHON_ENV_COMMAND = "import sys; print('Python %s on %s' % (sys.version, sys.platform))\n";

  private static final long APPROPRIATE_TO_WAIT = 60000;

  protected PydevConsoleRunner(@NotNull final Project project,
                               @NotNull Sdk sdk, @NotNull final PyConsoleType consoleType,
                               @NotNull final CommandLineArgumentsProvider commandLineArgumentsProvider,
                               @Nullable final String workingDir,
                               int[] ports) {
    super(project, consoleType.getTitle(), workingDir);
    mySdk = sdk;
    myConsoleType = consoleType;
    myCommandLineArgumentsProvider = commandLineArgumentsProvider;
    myPorts = ports;
  }

  @Nullable
  public static PydevConsoleRunner createAndRun(@NotNull final Project project,
                                                @NotNull final Sdk sdk,
                                                final PyConsoleType consoleType,
                                                final String projectRoot,
                                                final String... statements2execute) {
    return createAndRun(project, sdk, consoleType, projectRoot, createDefaultEnvironmentVariables(sdk), statements2execute);
  }

  public static Map<String, String> createDefaultEnvironmentVariables(Sdk sdk) {
    Map<String, String> envs = Maps.newHashMap();
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
    List<AnAction> actions = super.fillToolBarActions(toolbarActions, defaultExecutor, contentDescriptor);
    actions.add(backspaceHandlingAction);
    actions.add(interruptAction);
    return actions;
  }

  @Nullable
  public static PydevConsoleRunner createAndRun(@NotNull final Project project,
                                                @NotNull final Sdk sdk,
                                                @NotNull final PyConsoleType consoleType,
                                                @Nullable final String projectRoot,
                                                @NotNull final Map<String, String> environmentVariables,
                                                final String... statements2execute) {
    final PydevConsoleRunner consoleRunner = create(project, sdk, consoleType, projectRoot, environmentVariables);
    if (consoleRunner == null) return null;
    consoleRunner.run(statements2execute);
    return consoleRunner;
  }

  public void run(final String... statements2execute) {
    ProgressManager.getInstance().run(new Task.Backgroundable(getProject(), "Connecting to console", false) {
      public void run(@NotNull final ProgressIndicator indicator) {
        indicator.setText("Connecting to console...");
        try {
          initAndRun(statements2execute);
        }
        catch (ExecutionException e) {
          LOG.error("Error running console", e);
          ExecutionHelper.showErrors(myProject, Arrays.<Exception>asList(e), getTitle(), null);
        }
      }
    });
  }

  public static PydevConsoleRunner create(Project project,
                                          Sdk sdk,
                                          PyConsoleType consoleType,
                                          String projectRoot) {
    return create(project, sdk, consoleType, projectRoot, createDefaultEnvironmentVariables(sdk));
  }

  private static PydevConsoleRunner create(@NotNull Project project,
                                           @NotNull Sdk sdk,
                                           @NotNull PyConsoleType consoleType,
                                           @Nullable String projectRoot,
                                           @NotNull final Map<String, String> environmentVariables) {
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
    final CommandLineArgumentsProvider provider = new CommandLineArgumentsProvider() {
      public String[] getArguments() {
        return ArrayUtil.toStringArray(args);
      }

      public boolean passParentEnvs() {
        return false;
      }

      public Map<String, String> getAdditionalEnvs() {
        return environmentVariables;
      }
    };

    return new PydevConsoleRunner(project, sdk, consoleType, provider, projectRoot, ports);
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
    if (mySdk.getSdkAdditionalData() instanceof PythonRemoteSdkAdditionalData) {
      PythonRemoteInterpreterManager manager = PythonRemoteInterpreterManager.getInstance();
      if (manager != null) {
        try {
          PythonRemoteSdkAdditionalData data = (PythonRemoteSdkAdditionalData)mySdk.getSdkAdditionalData();

          GeneralCommandLine commandLine = new GeneralCommandLine(myCommandLineArgumentsProvider.getArguments());

          commandLine.getParametersList().set(1, PythonRemoteInterpreterManager.toSystemDependent(new File(data.getPyCharmHelpersPath(),
                                                                                                           PYDEV_PYDEVCONSOLE_PY)
                                                                                                    .getPath(),
                                                                                                  PySourcePosition.isWindowsPath(
                                                                                                    data.getInterpreterPath())));
          commandLine.getParametersList().set(2, "0");
          commandLine.getParametersList().set(3, "0");

          myCommandLine = commandLine.getCommandLineString();


          PyRemoteSshProcess remoteProcess =
            manager.createRemoteProcess(getProject(), data, commandLine);


          Scanner s = new Scanner(remoteProcess.getInputStream());
          boolean received = false;
          long started = System.currentTimeMillis();
          while (!received && (System.currentTimeMillis() - started < 2000)) {
            try {
              int port = s.nextInt();
              int port2 = s.nextInt();
              received = true;
              remoteProcess.addLocalTunnel(myPorts[0], data.getHost(), port);
              remoteProcess.addRemoteTunnel(port2, "localhost", myPorts[1]);
            }
            catch (Exception e) {
              try {
                Thread.sleep(200);
              }
              catch (InterruptedException e1) {
              }
            }
          }
          if (!received) {
            throw new ExecutionException("Couldn't get remote ports for console connection.");
          }
          try {
            myPydevConsoleCommunication = new PydevConsoleCommunication(getProject(), myPorts[0], remoteProcess, myPorts[1]);
            return remoteProcess;
          }
          catch (Exception e) {
            throw new ExecutionException(e.getMessage());
          }
        }
        catch (PyRemoteInterpreterException e) {
          throw new ExecutionException("Can't start remote console", e);
        }
      }
      throw new PythonRemoteInterpreterManager.PyRemoteInterpreterExecutionException();
    }
    else {
      myCommandLine = myCommandLineArgumentsProvider.getCommandLineString();
      final Process server = Runner
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
    return text.length() >= indentSize && StringUtils.isWhitespace(text.substring(text.length() - indentSize));
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
            myPydevConsoleCommunication.close();
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

  @NotNull
  @Override
  protected ConsoleExecuteActionHandler createConsoleExecuteActionHandler() {
    myConsoleExecuteActionHandler =
      new PydevConsoleExecuteActionHandler(getConsoleView(), getProcessHandler(), myPydevConsoleCommunication);
    myConsoleExecuteActionHandler.setEnabled(false);
    new ConsoleHistoryController(myConsoleType.getTypeId(), "", getLanguageConsole(),
                                 myConsoleExecuteActionHandler.getConsoleHistoryModel()).install();
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
    return element.getContainingFile().getCopyableUserData(CONSOLE_KEY);
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

  private void fireConsoleInitializedEvent(LanguageConsoleViewImpl consoleView) {
    for (ConsoleListener listener : myConsoleListeners) {
      listener.handleConsoleInitialized(consoleView);
    }
  }

  public interface ConsoleListener {
    void handleConsoleInitialized(LanguageConsoleViewImpl consoleView);
  }
}
