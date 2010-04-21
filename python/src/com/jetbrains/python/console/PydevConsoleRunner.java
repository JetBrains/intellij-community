package com.jetbrains.python.console;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.console.LanguageConsoleImpl;
import com.intellij.execution.console.LanguageConsoleViewImpl;
import com.intellij.execution.process.CommandLineArgumentsProvider;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.util.PathUtil;
import com.intellij.util.net.NetUtils;
import com.jetbrains.django.run.ExecutionHelper;
import com.jetbrains.django.run.Runner;
import com.jetbrains.django.util.DjangoUtil;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.console.pydev.ICallback;
import com.jetbrains.python.console.pydev.InterpreterResponse;
import com.jetbrains.python.console.pydev.PydevConsoleCommunication;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

/**
 * @author oleg
 */
public class PydevConsoleRunner extends PyConsoleRunner {
  private final int[] myPorts;
  private PydevConsoleCommunication myPydevConsoleCommunication;
  public static Key<PydevConsoleCommunication> CONSOLE_KEY = new Key<PydevConsoleCommunication>("PYDEV_CONSOLE_KEY");
  private static final String PYTHON_ENV_COMMAND = "import sys; print('Python %s on %s' % (sys.version, sys.platform))\n";

  protected PydevConsoleRunner(@NotNull final Project project,
                               @NotNull final String consoleTitle,
                               @NotNull final CommandLineArgumentsProvider provider,
                               @Nullable final String workingDir,
                               int[] ports) {
    super(project, consoleTitle, provider, workingDir);
    myPorts = ports;
  }

  public static void run(@NotNull final Project project,
                         @NotNull final Sdk sdk,
                         final String consoleTitle,
                         final String projectRoot,
                         final String ... statements2execute) {
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
    final ArrayList<String> args = new ArrayList<String>(
      Arrays.asList(sdk.getHomePath(), "-u", PythonHelpersLocator.getHelperPath("pydev/console/pydevconsole.py")));
    args.add(getLocalHostString());
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
        return Collections.emptyMap();
      }
    };

    final PydevConsoleRunner consoleRunner = new PydevConsoleRunner(project, consoleTitle, provider, projectRoot, ports);
    try {
      consoleRunner.initAndRun(statements2execute);
    }
    catch (ExecutionException e) {
      ExecutionHelper.showErrors(project, Arrays.<Exception>asList(e), consoleTitle, null);
    }
  }

  @Override
  protected LanguageConsoleViewImpl createConsoleView() {
    return new PydevLanguageConsoleView(myProject, myConsoleTitle);
  }

  @Override
  protected Process createProcess() throws ExecutionException {
    final Process server = Runner.createProcess(myWorkingDir, true, myProvider.getAdditionalEnvs(), myProvider.getArguments());
    try {
      myPydevConsoleCommunication = new PydevConsoleCommunication(getProject(), myPorts[0], server, myPorts[1]);
    }
    catch (Exception e) {
      throw new ExecutionException(e.getMessage());
    }
    return server;
  }

  public void initAndRun(final String ... statements2execute) throws ExecutionException {
    super.initAndRun();

    // Propagate console communication to language console
    ((PydevLanguageConsoleView)myConsoleView).setPydevConsoleCommunication(myPydevConsoleCommunication);

    try {
      Thread.sleep(300);
    }
    catch (InterruptedException e) {
      // Ignore
    }

    // Make executed statements visible to developers
    final LanguageConsoleImpl console = myConsoleView.getConsole();
    PyConsoleHighlightingUtil.processOutput(console, PYTHON_ENV_COMMAND, ProcessOutputTypes.SYSTEM);
    sendInput(PYTHON_ENV_COMMAND);
    for (String statement : statements2execute) {
      PyConsoleHighlightingUtil.processOutput(console, statement + "\n", ProcessOutputTypes.SYSTEM);
      sendInput(statement+"\n");
    }
  }

  @Override
  protected AnAction createCloseAction(final Executor defaultExecutor, final RunContentDescriptor myDescriptor) {
    final AnAction generalCloseAction = super.createCloseAction(defaultExecutor, myDescriptor);
    final AnAction closeAction = new AnAction() {
      @Override
      public void update(AnActionEvent e) {
        generalCloseAction.update(e);
      }

      @Override
      public void actionPerformed(AnActionEvent e) {
        if (myPydevConsoleCommunication != null) {
          try {
            myPydevConsoleCommunication.close();
          }
          catch (Exception e1) {
            // Ignore
          }
          generalCloseAction.actionPerformed(e);
        }
      }
    };
    closeAction.copyFrom(generalCloseAction);
    return closeAction;
  }

  @Override
  protected AnAction createStopAction() {
    final AnAction generalStopAction = super.createStopAction();
    final AnAction stopAction = new AnAction() {
      @Override
      public void update(AnActionEvent e) {
        generalStopAction.update(e);
      }

      @Override
      public void actionPerformed(AnActionEvent e) {
        if (myPydevConsoleCommunication != null) {
          try {
            myPydevConsoleCommunication.close();
          }
          catch (Exception e1) {
            // Ignore
          }
          generalStopAction.actionPerformed(e);
        }
      }
    };
    stopAction.copyFrom(generalStopAction);
    return stopAction;
  }

  @Override
  protected void sendInput(final String input) {
    if (myPydevConsoleCommunication != null){
      myPydevConsoleCommunication.execInterpreter(input, new ICallback<Object, InterpreterResponse>() {
        public Object call(final InterpreterResponse interpreterResponse) {
          final LanguageConsoleImpl console = myConsoleView.getConsole();
          // Handle prompt
          if (interpreterResponse.more){
            if (!PyConsoleHighlightingUtil.INDENT_PROMPT.equals(console.getPrompt())){
              console.setPrompt(PyConsoleHighlightingUtil.INDENT_PROMPT);
            }
          } else {
            if (!PyConsoleHighlightingUtil.ORDINARY_PROMPT.equals(console.getPrompt())){
              console.setPrompt(PyConsoleHighlightingUtil.ORDINARY_PROMPT);
            }
          }
          // Handle output
          if (!StringUtil.isEmpty(interpreterResponse.err)){
            PyConsoleHighlightingUtil.processOutput(console, interpreterResponse.err, ProcessOutputTypes.STDERR);
          } else {
            PyConsoleHighlightingUtil.processOutput(console, interpreterResponse.out, ProcessOutputTypes.STDOUT);
          }
          return null;
        }
      });
    }
  }

  public static boolean isInPydevConsole(final PsiElement element){
    return getConsoleCommunication(element) != null;
  }

  @Nullable
  public static PydevConsoleCommunication getConsoleCommunication(final PsiElement element) {
    return element.getContainingFile().getCopyableUserData(CONSOLE_KEY);
  }

  public static String getLocalHostString() {
    // HACK for Windows with ipv6
    String localHostString = "localhost";
    try {
      final InetAddress localHost = InetAddress.getByName(localHostString);
      if (localHost.getAddress().length != 4 && SystemInfo.isWindows){
        localHostString = "127.0.0.1";
      }
    }
    catch (UnknownHostException e) {
      // ignore
    }
    return localHostString;
  }

}
