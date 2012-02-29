package com.jetbrains.python.debugger;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.console.ConsoleHistoryController;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.AbstractConsoleRunnerWithHistory;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.GenericProgramRunner;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugProcessStarter;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.console.PythonConsoleView;
import com.jetbrains.python.console.PythonDebugConsoleCommunication;
import com.jetbrains.python.console.PythonDebugLanguageConsoleView;
import com.jetbrains.python.console.pydev.ConsoleCommunication;
import com.jetbrains.python.run.AbstractPythonRunConfiguration;
import com.jetbrains.python.run.CommandLinePatcher;
import com.jetbrains.python.run.PythonCommandLineState;
import com.jetbrains.python.run.PythonRunConfiguration;
import com.jetbrains.python.sdk.PythonSdkFlavor;
import com.jetbrains.rest.run.RestRunConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;

/**
 * @author yole
 */
public class PyDebugRunner extends GenericProgramRunner {
  public static final String PY_DEBUG_RUNNER = "PyDebugRunner";

  public static final String DEBUGGER_MAIN = "pydev/pydevd.py";
  public static final String CLIENT_PARAM = "--client";
  public static final String PORT_PARAM = "--port";
  public static final String FILE_PARAM = "--file";

  @NotNull
  public String getRunnerId() {
    return PY_DEBUG_RUNNER;
  }

  public boolean canRun(@NotNull String executorId, @NotNull RunProfile profile) {
    return DefaultDebugExecutor.EXECUTOR_ID.equals(executorId) &&
           profile instanceof AbstractPythonRunConfiguration &&
           !(profile instanceof RestRunConfiguration);
  }

  protected RunContentDescriptor doExecute(final Project project, Executor executor, RunProfileState profileState,
                                           RunContentDescriptor contentToReuse,
                                           ExecutionEnvironment env) throws ExecutionException {
    FileDocumentManager.getInstance().saveAllDocuments();

    final PythonCommandLineState pyState = (PythonCommandLineState)profileState;
    final Pair<ServerSocket, Integer> serverSocket = pyState.createDebugServerSocket();
    final int serverLocalPort = serverSocket.getSecond();
    RunProfile profile = env.getRunProfile();
    final ExecutionResult result = pyState.execute(executor, createCommandLinePatchers(pyState, profile, serverLocalPort));

    final XDebugSession session = XDebuggerManager.getInstance(project).
      startSession(this, env, contentToReuse, new XDebugProcessStarter() {
        @NotNull
        public XDebugProcess start(@NotNull final XDebugSession session) {
          PyDebugProcess pyDebugProcess =
            new PyDebugProcess(session, serverSocket.getFirst(), result.getExecutionConsole(), result.getProcessHandler(),
                               pyState.isMultiprocessDebug());

          createConsoleCommunicationAndSetupActions(project, result, pyDebugProcess);


          return pyDebugProcess;
        }
      });
    return session.getRunContentDescriptor();
  }

  public static int findIndex(List<String> paramList, String paramName) {
    for (int i = 0; i < paramList.size(); i++) {
      if (paramName.equals(paramList.get(i))) {
        return i + 1;
      }
    }
    return -1;
  }

  protected static void createConsoleCommunicationAndSetupActions(@NotNull final Project project,
                                                                  @NotNull final ExecutionResult result,
                                                                  @NotNull PyDebugProcess debugProcess) {
    ExecutionConsole console = result.getExecutionConsole();
    ProcessHandler processHandler = result.getProcessHandler();

    if (console instanceof PythonDebugLanguageConsoleView) {
      PythonConsoleView pythonConsoleView = ((PythonDebugLanguageConsoleView)console).getPydevConsoleView();


      ConsoleCommunication consoleCommunication =
        new PythonDebugConsoleCommunication(project, debugProcess);
      pythonConsoleView.setConsoleCommunication(consoleCommunication);


      PydevDebugConsoleExecuteActionHandler consoleExecuteActionHandler = new PydevDebugConsoleExecuteActionHandler(pythonConsoleView,
                                                                                                                    processHandler,
                                                                                                                    consoleCommunication);

      pythonConsoleView.setExecutionHandler(consoleExecuteActionHandler);

      debugProcess.getSession().addSessionListener(consoleExecuteActionHandler);
      new ConsoleHistoryController("py", "", pythonConsoleView.getConsole(), consoleExecuteActionHandler.getConsoleHistoryModel())
        .install();
      final AnAction execAction = AbstractConsoleRunnerWithHistory
        .createConsoleExecAction(pythonConsoleView.getConsole(), processHandler, consoleExecuteActionHandler);
      execAction.registerCustomShortcutSet(execAction.getShortcutSet(), pythonConsoleView.getComponent());
    }
  }

  @Nullable
  private static CommandLinePatcher createRunConfigPatcher(RunProfileState state, RunProfile profile) {
    CommandLinePatcher runConfigPatcher = null;
    if (state instanceof PythonCommandLineState && profile instanceof PythonRunConfiguration) {
      runConfigPatcher = (PythonRunConfiguration)profile;
    }
    return runConfigPatcher;
  }

  public static CommandLinePatcher[] createCommandLinePatchers(final PythonCommandLineState state,
                                                               RunProfile profile,
                                                               final int serverLocalPort) {
    return new CommandLinePatcher[]{createDebugServerPatcher(state, serverLocalPort), createRunConfigPatcher(state, profile)};
  }

  private static CommandLinePatcher createDebugServerPatcher(final PythonCommandLineState pyState, final int serverLocalPort) {
    return new CommandLinePatcher() {
      public void patchCommandLine(GeneralCommandLine commandLine) {


        // script name is the last parameter; all other params are for python interpreter; insert just before name
        final ParametersList parametersList = commandLine.getParametersList();

        @SuppressWarnings("ConstantConditions") @NotNull
        ParamsGroup debugParams = parametersList.getParamsGroup(PythonCommandLineState.GROUP_DEBUGGER);

        @SuppressWarnings("ConstantConditions") @NotNull
        ParamsGroup exeParams = parametersList.getParamsGroup(PythonCommandLineState.GROUP_EXE_OPTIONS);

        final PythonSdkFlavor flavor = pyState.getSdkFlavor();
        if (flavor != null) {
          for (String option : flavor.getExtraDebugOptions()) {
            exeParams.addParameter(option);
          }
        }

        fillDebugParameters(debugParams, serverLocalPort, pyState);
      }
    };
  }

  private static void fillDebugParameters(ParamsGroup debugParams, int serverLocalPort, PythonCommandLineState pyState) {
    debugParams.addParameter(PythonHelpersLocator.getHelperPath(DEBUGGER_MAIN));
    if (pyState.isMultiprocessDebug()) {
      debugParams.addParameter("--multiproc");
    }

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      debugParams.addParameter("--DEBUG");
    }

    final String[] debuggerArgs = new String[]{
      CLIENT_PARAM, "127.0.0.1",
      PORT_PARAM, String.valueOf(serverLocalPort),
      FILE_PARAM
    };
    for (String s : debuggerArgs) {
      debugParams.addParameter(s);
    }
  }
}
