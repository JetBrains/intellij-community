package com.jetbrains.python.console;

import com.google.common.collect.ImmutableMap;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionHelper;
import com.intellij.execution.Executor;
import com.intellij.execution.console.LanguageConsoleImpl;
import com.intellij.execution.console.LanguageConsoleViewImpl;
import com.intellij.execution.process.CommandLineArgumentsProvider;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.runners.AbstractConsoleRunnerWithHistory;
import com.intellij.execution.runners.ConsoleExecuteActionHandler;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.psi.PsiElement;
import com.intellij.util.net.NetUtils;
import com.jetbrains.django.run.Runner;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.console.pydev.ConsoleCommunication;
import com.jetbrains.python.console.pydev.PydevConsoleCommunication;
import com.jetbrains.python.sdk.JythonSdkFlavor;
import com.jetbrains.python.sdk.PythonSdkFlavor;
import org.apache.xmlrpc.XmlRpcException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

/**
 * @author oleg
 */
public class PydevConsoleRunner extends AbstractConsoleRunnerWithHistory {
  private Sdk mySdk;
  private final int[] myPorts;
  private PydevConsoleCommunication myPydevConsoleCommunication;
  private PyConsoleProcessHandler myProcessHandler;

  public static Key<ConsoleCommunication> CONSOLE_KEY = new Key<ConsoleCommunication>("PYDEV_CONSOLE_KEY");

  private static final String PYTHON_ENV_COMMAND = "import sys; print('Python %s on %s' % (sys.version, sys.platform))\n";

  private static final long APPROPRIATE_TO_WAIT = 10000;

  protected PydevConsoleRunner(@NotNull final Project project,
                               @NotNull Sdk sdk, @NotNull final String consoleTitle,
                               @NotNull final CommandLineArgumentsProvider commandLineArgumentsProvider,
                               @Nullable final String workingDir,
                               int[] ports) {
    super(project, consoleTitle, commandLineArgumentsProvider, workingDir);
    mySdk = sdk;
    myPorts = ports;
  }

  public static void run(@NotNull final Project project,
                         @NotNull final Sdk sdk,
                         final String consoleTitle,
                         final String projectRoot,
                         final String... statements2execute) {
    final int[] ports;
    try {
      // File "pydev/console/pydevconsole.py", line 223, in <module>
      // port, client_port = sys.argv[1:3]
      ports = NetUtils.findAvailableSocketPorts(2);
    }
    catch (IOException e) {
      ExecutionHelper.showErrors(project, Arrays.<Exception>asList(e), consoleTitle, null);
      return;
    }
    final ArrayList<String> args = new ArrayList<String>();
    args.add(sdk.getHomePath());
    final String versionString = sdk.getVersionString();
    if (versionString == null || !versionString.toLowerCase().contains("jython")) {
      args.add("-u");
    }
    args.add(FileUtil.toSystemDependentName(PythonHelpersLocator.getHelperPath("pydev/console/pydevconsole.py")));
    for (int port : ports) {
      args.add(String.valueOf(port));
    }
    final CommandLineArgumentsProvider provider = new CommandLineArgumentsProvider() {
      public String[] getArguments() {
        return args.toArray(new String[args.size()]);
      }

      public boolean passParentEnvs() {
        return false;
      }

      public Map<String, String> getAdditionalEnvs() {
        return ImmutableMap.of("PYTHONIOENCODING", "utf-8");
      }
    };

    final PydevConsoleRunner consoleRunner = new PydevConsoleRunner(project, sdk, consoleTitle, provider, projectRoot, ports);
    try {
      consoleRunner.initAndRun(statements2execute);
    }
    catch (ExecutionException e) {
      ExecutionHelper.showErrors(project, Arrays.<Exception>asList(e), consoleTitle, null);
    }
  }

  @Override
  protected LanguageConsoleViewImpl createConsoleView() {
    return new PydevLanguageConsoleView(getProject(), getConsoleTitle());
  }

  @Override
  protected Process createProcess(CommandLineArgumentsProvider provider) throws ExecutionException {
    final Process server = Runner.createProcess(getWorkingDir(), provider.getAdditionalEnvs(), provider.getArguments());
    try {
      myPydevConsoleCommunication = new PydevConsoleCommunication(getProject(), myPorts[0], server, myPorts[1]);
    }
    catch (Exception e) {
      throw new ExecutionException(e.getMessage());
    }
    return server;
  }

  @Override
  protected PyConsoleProcessHandler createProcessHandler(final Process process, final String commandLine) {
    myProcessHandler = new PyConsoleProcessHandler(process, getConsoleView().getConsole(), myPydevConsoleCommunication, commandLine,
                                                   CharsetToolkit.UTF8_CHARSET);
    return myProcessHandler;
  }

  public void initAndRun(final String... statements2execute) throws ExecutionException {
    super.initAndRun();

    // Propagate console communication to language console
    ((PydevLanguageConsoleView)getConsoleView()).setConsoleCommunication(myPydevConsoleCommunication);

    final LanguageConsoleImpl console = getConsoleView().getConsole();

    //TODO: it is a dirty hack, implement proper handshake for processes
    if (PythonSdkFlavor.getFlavor(mySdk.getHomePath()) instanceof JythonSdkFlavor) {
      try {
        Thread.sleep(3000);
      }
      catch (InterruptedException e) {
      }
    }

    if (handshake()) {
      // Make executed statements visible to developers
      PyConsoleHighlightingUtil.processOutput(console, PYTHON_ENV_COMMAND, ProcessOutputTypes.SYSTEM);
      getConsoleExecuteActionHandler().processLine(PYTHON_ENV_COMMAND);

      for (String statement : statements2execute) {
        PyConsoleHighlightingUtil.processOutput(console, statement + "\n", ProcessOutputTypes.SYSTEM);
        getConsoleExecuteActionHandler().processLine(statement + "\n");
      }
    }
    else {
      PyConsoleHighlightingUtil.processOutput(console, "Couldn't connect to console process.", ProcessOutputTypes.STDERR);
      myProcessHandler.destroyProcess();
      finishConsole();
    }
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
    final AnAction stopAction = new AnAction() {
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
    return new PydevConsoleExecuteActionHandler(getConsoleView(), getProcessHandler(), myPydevConsoleCommunication);
  }


  public static boolean isInPydevConsole(final PsiElement element) {
    return element instanceof PydevConsoleElement || getConsoleCommunication(element) != null;
  }

  @Nullable
  public static ConsoleCommunication getConsoleCommunication(final PsiElement element) {
    return element.getContainingFile().getCopyableUserData(CONSOLE_KEY);
  }
}
