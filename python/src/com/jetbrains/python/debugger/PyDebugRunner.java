package com.jetbrains.python.debugger;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
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
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugProcessStarter;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.jetbrains.appengine.util.StringUtils;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.console.PythonConsoleView;
import com.jetbrains.python.console.PythonDebugConsoleCommunication;
import com.jetbrains.python.console.PythonDebugLanguageConsoleView;
import com.jetbrains.python.console.pydev.ConsoleCommunication;
import com.jetbrains.python.run.AbstractPythonRunConfiguration;
import com.jetbrains.python.run.CommandLinePatcher;
import com.jetbrains.python.run.PythonCommandLineState;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author yole
 */
public class PyDebugRunner extends GenericProgramRunner {
  public static final String PY_DEBUG_RUNNER = "PyDebugRunner";

  public static final String DEBUGGER_MAIN = "pydev/pydevd.py";
  public static final String CLIENT_PARAM = "--client";
  public static final String PORT_PARAM = "--port";
  public static final String FILE_PARAM = "--file";
  public static final String PYCHARM_PROJECT_ROOTS = "PYCHARM_PROJECT_ROOTS";

  @NotNull
  public String getRunnerId() {
    return PY_DEBUG_RUNNER;
  }

  public boolean canRun(@NotNull String executorId, @NotNull RunProfile profile) {
    return DefaultDebugExecutor.EXECUTOR_ID.equals(executorId) &&
           profile instanceof AbstractPythonRunConfiguration &&
           ((AbstractPythonRunConfiguration)profile).canRunWithCoverage();
  }

  protected RunContentDescriptor doExecute(final Project project, Executor executor, RunProfileState profileState,
                                           RunContentDescriptor contentToReuse,
                                           ExecutionEnvironment env) throws ExecutionException {
    FileDocumentManager.getInstance().saveAllDocuments();

    final PythonCommandLineState pyState = (PythonCommandLineState)profileState;
    final ServerSocket serverSocket = PythonCommandLineState.createServerSocket();
    final int serverLocalPort = serverSocket.getLocalPort();
    RunProfile profile = env.getRunProfile();
    final ExecutionResult result = pyState.execute(executor, createCommandLinePatchers(project, pyState, profile, serverLocalPort));

    final XDebugSession session = XDebuggerManager.getInstance(project).
      startSession(this, env, contentToReuse, new XDebugProcessStarter() {
        @NotNull
        public XDebugProcess start(@NotNull final XDebugSession session) {
          PyDebugProcess pyDebugProcess =
            new PyDebugProcess(session, serverSocket, result.getExecutionConsole(), result.getProcessHandler(),
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
    if (state instanceof PythonCommandLineState && profile instanceof AbstractPythonRunConfiguration) {
      runConfigPatcher = (AbstractPythonRunConfiguration)profile;
    }
    return runConfigPatcher;
  }

  public static CommandLinePatcher[] createCommandLinePatchers(final Project project, final PythonCommandLineState state,
                                                               RunProfile profile,
                                                               final int serverLocalPort) {
    return new CommandLinePatcher[]{createDebugServerPatcher(project, state, serverLocalPort), createRunConfigPatcher(state, profile)};
  }

  private static CommandLinePatcher createDebugServerPatcher(final Project project,
                                                             final PythonCommandLineState pyState,
                                                             final int serverLocalPort) {
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

        fillDebugParameters(project, debugParams, serverLocalPort, pyState, commandLine);
      }
    };
  }

  private static void fillDebugParameters(@NotNull Project project,
                                          @NotNull ParamsGroup debugParams,
                                          int serverLocalPort,
                                          @NotNull PythonCommandLineState pyState,
                                          @NotNull GeneralCommandLine generalCommandLine) {
    debugParams.addParameter(PythonHelpersLocator.getHelperPath(DEBUGGER_MAIN));
    if (pyState.isMultiprocessDebug()) {
      debugParams.addParameter("--multiproc");
    }

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      debugParams.addParameter("--DEBUG");
    }

    if (PyDebuggerOptionsProvider.getInstance(project).isSaveCallSignatures()) {
      debugParams.addParameter("--save-signatures");
      addProjectRootsToEnv(project, generalCommandLine);
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

  private static void addProjectRootsToEnv(@NotNull Project project, @NotNull GeneralCommandLine commandLine) {
    Map<String, String> params = commandLine.getEnvParams();

    if (params == null) {
      params = Maps.newHashMap();
      commandLine.setEnvParams(params);
    }

    List<String> roots = Lists.newArrayList();
    for (VirtualFile contentRoot : ProjectRootManager.getInstance(project).getContentRoots()) {
      roots.add(contentRoot.getPath());
    }

    params.put(PYCHARM_PROJECT_ROOTS, StringUtil.join(roots, File.pathSeparator));
  }
}
