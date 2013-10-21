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
package com.jetbrains.python.debugger;

import com.google.common.collect.Lists;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.configurations.*;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.GenericProgramRunner;
import com.intellij.execution.runners.LanguageConsoleBuilder;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
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
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
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
  public static final String PYCHARM_PROJECT_ROOTS = "PYCHARM_PROJECT_ROOTS";
  public static final String GEVENT_SUPPORT = "GEVENT_SUPPORT";

  @NotNull
  public String getRunnerId() {
    return PY_DEBUG_RUNNER;
  }

  public boolean canRun(@NotNull String executorId, @NotNull RunProfile profile) {
    return DefaultDebugExecutor.EXECUTOR_ID.equals(executorId) &&
           profile instanceof AbstractPythonRunConfiguration &&
           ((AbstractPythonRunConfiguration)profile).canRunWithCoverage();
  }

  protected RunContentDescriptor doExecute(final Project project, RunProfileState profileState,
                                           RunContentDescriptor contentToReuse,
                                           ExecutionEnvironment env) throws ExecutionException {
    FileDocumentManager.getInstance().saveAllDocuments();

    final PythonCommandLineState pyState = (PythonCommandLineState)profileState;
    final ServerSocket serverSocket = PythonCommandLineState.createServerSocket();
    final int serverLocalPort = serverSocket.getLocalPort();
    RunProfile profile = env.getRunProfile();
    final ExecutionResult result = pyState.execute(env.getExecutor(), createCommandLinePatchers(project, pyState, profile, serverLocalPort));

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


      ConsoleCommunication consoleCommunication = new PythonDebugConsoleCommunication(project, debugProcess);
      pythonConsoleView.setConsoleCommunication(consoleCommunication);

      PydevDebugConsoleExecuteActionHandler consoleExecuteActionHandler = new PydevDebugConsoleExecuteActionHandler(pythonConsoleView,
                                                                                                                    processHandler,
                                                                                                                    consoleCommunication);

      pythonConsoleView.setExecutionHandler(consoleExecuteActionHandler);

      debugProcess.getSession().addSessionListener(consoleExecuteActionHandler);
      new LanguageConsoleBuilder().console(pythonConsoleView).processHandler(processHandler).initActions(consoleExecuteActionHandler, "py");
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

    if (PyDebuggerOptionsProvider.getInstance(project).isSupportGeventDebugging()) {
      generalCommandLine.getEnvironment().put(GEVENT_SUPPORT, "True");
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

    List<String> roots = Lists.newArrayList();
    for (VirtualFile contentRoot : ProjectRootManager.getInstance(project).getContentRoots()) {
      roots.add(contentRoot.getPath());
    }

    commandLine.getEnvironment().put(PYCHARM_PROJECT_ROOTS, StringUtil.join(roots, File.pathSeparator));
  }
}
