// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.console.LanguageConsoleBuilder;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.GenericProgramRunner;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugProcessStarter;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.jetbrains.python.PythonHelper;
import com.jetbrains.python.console.PydevConsoleRunnerFactory;
import com.jetbrains.python.console.PythonConsoleView;
import com.jetbrains.python.console.PythonDebugConsoleCommunication;
import com.jetbrains.python.console.PythonDebugLanguageConsoleView;
import com.jetbrains.python.console.pydev.ConsoleCommunicationListener;
import com.jetbrains.python.debugger.settings.PyDebuggerSettings;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.run.AbstractPythonRunConfiguration;
import com.jetbrains.python.run.CommandLinePatcher;
import com.jetbrains.python.run.DebugAwareConfiguration;
import com.jetbrains.python.run.PythonCommandLineState;
import com.jetbrains.python.sdk.PythonEnvUtil;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import com.jetbrains.python.testing.AbstractPythonTestRunConfiguration;
import com.jetbrains.python.testing.PyAbstractTestConfiguration;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author yole
 */
public class PyDebugRunner extends GenericProgramRunner {
  public static final @NonNls String PY_DEBUG_RUNNER = "PyDebugRunner";

  public static final @NonNls String DEBUGGER_MAIN = "pydev/pydevd.py";
  public static final @NonNls String CLIENT_PARAM = "--client";
  public static final @NonNls String PORT_PARAM = "--port";
  public static final @NonNls String FILE_PARAM = "--file";
  public static final @NonNls String MODULE_PARAM = "--module";
  public static final @NonNls String MULTIPROCESS_PARAM = "--multiprocess";
  public static final @NonNls String IDE_PROJECT_ROOTS = "IDE_PROJECT_ROOTS";
  public static final @NonNls String LIBRARY_ROOTS = "LIBRARY_ROOTS";
  public static final @NonNls String PYTHON_ASYNCIO_DEBUG = "PYTHONASYNCIODEBUG";
  @SuppressWarnings("SpellCheckingInspection")
  public static final @NonNls String GEVENT_SUPPORT = "GEVENT_SUPPORT";
  public static final @NonNls String PYDEVD_FILTERS = "PYDEVD_FILTERS";
  public static final @NonNls String PYDEVD_FILTER_LIBRARIES = "PYDEVD_FILTER_LIBRARIES";
  public static final @NonNls String PYDEVD_USE_CYTHON = "PYDEVD_USE_CYTHON";
  public static final @NonNls String CYTHON_EXTENSIONS_DIR = new File(PathManager.getSystemPath(), "cythonExtensions").toString();

  @Override
  @NotNull
  public String getRunnerId() {
    return PY_DEBUG_RUNNER;
  }

  @Override
  public boolean canRun(@NotNull final String executorId, @NotNull final RunProfile profile) {
    if (!DefaultDebugExecutor.EXECUTOR_ID.equals(executorId)) {
      // If not debug at all
      return false;
    }
    /*
     * Any python configuration is debuggable unless it explicitly declares itself as DebugAwareConfiguration and denies it
     * with canRunUnderDebug == false
     */

    if (profile instanceof WrappingRunConfiguration) {
      // If configuration is wrapper -- unwrap it and check
      return isDebuggable(((WrappingRunConfiguration<?>)profile).getPeer());
    }
    return isDebuggable(profile);
  }

  private static boolean isDebuggable(@NotNull final RunProfile profile) {
    if (profile instanceof DebugAwareConfiguration) {
      // if configuration knows whether debug is allowed
      return ((DebugAwareConfiguration)profile).canRunUnderDebug();
    }
    if (profile instanceof AbstractPythonRunConfiguration) {
      // Any python configuration is debuggable
      return true;
    }
    // No even a python configuration
    return false;
  }


  protected XDebugSession createSession(@NotNull RunProfileState state, @NotNull final ExecutionEnvironment environment)
    throws ExecutionException {
    FileDocumentManager.getInstance().saveAllDocuments();

    final PythonCommandLineState pyState = (PythonCommandLineState)state;

    Sdk sdk = pyState.getSdk();
    PyDebugSessionFactory sessionCreator = PyDebugSessionFactory.findExtension(sdk);
    if (sessionCreator != null) {
      return sessionCreator.createSession(pyState, environment);
    }

    final ServerSocket serverSocket = PythonCommandLineState.createServerSocket();
    final int serverLocalPort = serverSocket.getLocalPort();
    RunProfile profile = environment.getRunProfile();
    final ExecutionResult result =
      pyState.execute(environment.getExecutor(), createCommandLinePatchers(environment.getProject(), pyState, profile, serverLocalPort));

    return XDebuggerManager.getInstance(environment.getProject()).
      startSession(environment, new XDebugProcessStarter() {
        @Override
        @NotNull
        public XDebugProcess start(@NotNull final XDebugSession session) {
          PyDebugProcess pyDebugProcess =
            createDebugProcess(session, serverSocket, result, pyState);

          createConsoleCommunicationAndSetupActions(environment.getProject(), result, pyDebugProcess, session);
          return pyDebugProcess;
        }
      });
  }

  @NotNull
  protected PyDebugProcess createDebugProcess(@NotNull XDebugSession session,
                                              ServerSocket serverSocket,
                                              ExecutionResult result,
                                              PythonCommandLineState pyState) {
    return new PyDebugProcess(session, serverSocket, result.getExecutionConsole(), result.getProcessHandler(),
                              pyState.isMultiprocessDebug());
  }

  @Override
  protected RunContentDescriptor doExecute(@NotNull RunProfileState state, @NotNull final ExecutionEnvironment environment)
    throws ExecutionException {
    XDebugSession session = createSession(state, environment);
    initSession(session, state, environment.getExecutor());
    return session.getRunContentDescriptor();
  }

  protected void initSession(XDebugSession session, RunProfileState state, Executor executor) {
  }

  public static int findIndex(List<String> paramList, String paramName) {
    for (int i = 0; i < paramList.size(); i++) {
      if (paramName.equals(paramList.get(i))) {
        return i + 1;
      }
    }
    return -1;
  }

  public static void createConsoleCommunicationAndSetupActions(@NotNull final Project project,
                                                               @NotNull final ExecutionResult result,
                                                               @NotNull PyDebugProcess debugProcess, @NotNull XDebugSession session) {
    ExecutionConsole console = result.getExecutionConsole();
    if (console instanceof PythonDebugLanguageConsoleView) {
      ProcessHandler processHandler = result.getProcessHandler();

      initDebugConsoleView(project, debugProcess, (PythonDebugLanguageConsoleView)console, processHandler, session);
    }
  }

  public static PythonDebugConsoleCommunication initDebugConsoleView(Project project,
                                                                     PyDebugProcess debugProcess,
                                                                     PythonDebugLanguageConsoleView console,
                                                                     ProcessHandler processHandler, final XDebugSession session) {
    PythonConsoleView pythonConsoleView = console.getPydevConsoleView();
    PythonDebugConsoleCommunication debugConsoleCommunication =
      new PythonDebugConsoleCommunication(project, debugProcess, pythonConsoleView);

    pythonConsoleView.setConsoleCommunication(debugConsoleCommunication);


    PydevDebugConsoleExecuteActionHandler consoleExecuteActionHandler = new PydevDebugConsoleExecuteActionHandler(pythonConsoleView,
                                                                                                                  processHandler,
                                                                                                                  debugConsoleCommunication);
    pythonConsoleView.setExecutionHandler(consoleExecuteActionHandler);

    debugProcess.getSession().addSessionListener(consoleExecuteActionHandler);
    new LanguageConsoleBuilder(pythonConsoleView).processHandler(processHandler).initActions(consoleExecuteActionHandler, "py");


    debugConsoleCommunication.addCommunicationListener(new ConsoleCommunicationListener() {
      @Override
      public void commandExecuted(boolean more) {
        session.rebuildViews();
      }

      @Override
      public void inputRequested() {
      }
    });

    return debugConsoleCommunication;
  }

  @Nullable
  public static CommandLinePatcher createRunConfigPatcher(RunProfileState state, RunProfile profile) {
    CommandLinePatcher runConfigPatcher = null;
    if (state instanceof PythonCommandLineState && profile instanceof AbstractPythonRunConfiguration) {
      runConfigPatcher = (AbstractPythonRunConfiguration)profile;
    }
    return runConfigPatcher;
  }

  public CommandLinePatcher[] createCommandLinePatchers(final Project project, final PythonCommandLineState state,
                                                        RunProfile profile,
                                                        final int serverLocalPort) {
    return new CommandLinePatcher[]{createDebugServerPatcher(project, state, serverLocalPort, profile),
      createRunConfigPatcher(state, profile)};
  }

  public static boolean patchExeParams(ParametersList parametersList) {
    // we should remove '-m' parameter, but notify debugger of it
    // but we can't remove one parameter from group, so we create new parameters group
    int moduleParamsIndex =
      parametersList.getParamsGroups().indexOf(parametersList.getParamsGroup(PythonCommandLineState.GROUP_MODULE));
    ParamsGroup oldModuleParams = parametersList.removeParamsGroup(moduleParamsIndex);
    if (oldModuleParams == null) {
      return false;
    }
    boolean isModule = false;

    ParamsGroup newModuleParams = new ParamsGroup(PythonCommandLineState.GROUP_MODULE);
    for (String param : oldModuleParams.getParameters()) {
      if (!param.equals("-m")) {
        newModuleParams.addParameter(param);
      }
      else {
        isModule = true;
      }
    }

    parametersList.addParamsGroupAt(moduleParamsIndex, newModuleParams);
    return isModule;
  }

  private CommandLinePatcher createDebugServerPatcher(final Project project,
                                                      final PythonCommandLineState pyState,
                                                      final int serverLocalPort,
                                                      final RunProfile profile) {
    return new CommandLinePatcher() {
      @Override
      public void patchCommandLine(GeneralCommandLine commandLine) {
        // script name is the last parameter; all other params are for python interpreter; insert just before name
        ParametersList parametersList = commandLine.getParametersList();

        @SuppressWarnings("ConstantConditions") @NotNull
        ParamsGroup debugParams = parametersList.getParamsGroup(PythonCommandLineState.GROUP_DEBUGGER);
        assert debugParams != null;

        boolean isModule = patchExeParams(parametersList);

        fillDebugParameters(project, debugParams, serverLocalPort, pyState, commandLine, profile, isModule);

        @SuppressWarnings("ConstantConditions") @NotNull
        ParamsGroup exeParams = parametersList.getParamsGroup(PythonCommandLineState.GROUP_EXE_OPTIONS);

        final PythonSdkFlavor flavor = pyState.getSdkFlavor();
        if (flavor != null) {
          assert exeParams != null;
          for (String option : flavor.getExtraDebugOptions()) {
            exeParams.addParameter(option);
          }
        }
      }
    };
  }

  private void fillDebugParameters(@NotNull Project project,
                                   @NotNull ParamsGroup debugParams,
                                   int serverLocalPort,
                                   @NotNull PythonCommandLineState pyState,
                                   @NotNull GeneralCommandLine cmd,
                                   @Nullable RunProfile runProfile,
                                   boolean isModule) {
    PythonHelper.DEBUGGER.addToGroup(debugParams, cmd);

    if (isModule) {
      // add module flag only after command line parameters
      debugParams.addParameter(MODULE_PARAM);
    }

    configureDebugParameters(project, debugParams, pyState, cmd);

    configureDebugEnvironment(project, cmd.getEnvironment(), runProfile);

    configureDebugConnectionParameters(debugParams, serverLocalPort);
  }

  public static void configureDebugEnvironment(@NotNull Project project, Map<String, String> environment,
                                               @Nullable RunProfile runProfile) {
    if (PyDebuggerOptionsProvider.getInstance(project).isSupportGeventDebugging()) {
      environment.put(GEVENT_SUPPORT, "True");
    }

    PyDebuggerSettings debuggerSettings = PyDebuggerSettings.getInstance();
    if (debuggerSettings.isSteppingFiltersEnabled()) {
      environment.put(PYDEVD_FILTERS, debuggerSettings.getSteppingFiltersForProject(project));
    }
    if (debuggerSettings.isLibrariesFilterEnabled()) {
      environment.put(PYDEVD_FILTER_LIBRARIES, "True");
    }
    if (debuggerSettings.getValuesPolicy() != PyDebugValue.ValuesPolicy.SYNC) {
      environment.put(PyDebugValue.POLICY_ENV_VARS.get(debuggerSettings.getValuesPolicy()), "True");
    }

    PydevConsoleRunnerFactory.putIPythonEnvFlag(project, environment);

    PythonEnvUtil.addToPythonPath(environment, CYTHON_EXTENSIONS_DIR);

    addProjectRootsToEnv(project, environment);

    final AbstractPythonRunConfiguration runConfiguration = runProfile instanceof AbstractPythonRunConfiguration ?
                                                            (AbstractPythonRunConfiguration)runProfile : null;
    if (runConfiguration != null) {
      final Sdk sdk = runConfiguration.getSdk();
      if (sdk != null) {
        final PythonSdkFlavor flavor = PythonSdkFlavor.getFlavor(sdk);
        if (flavor != null) {
          final LanguageLevel langLevel = flavor.getLanguageLevel(sdk);
          // PY-28457 Disable Cython extensions in Python 3.4 and Python 3.5 because of crash in generated C code
          if (langLevel == LanguageLevel.PYTHON34 || langLevel == LanguageLevel.PYTHON35) {
            environment.put(PYDEVD_USE_CYTHON, "NO");
          }
        }
      }

      addSdkRootsToEnv(environment, runConfiguration);
      PythonEnvUtil.addToPythonPath(environment, runConfiguration.getWorkingDirectorySafe());
    }
  }

  protected void configureDebugParameters(@NotNull Project project,
                                          @NotNull ParamsGroup debugParams,
                                          @NotNull PythonCommandLineState pyState,
                                          @NotNull GeneralCommandLine cmd) {
    if (pyState.isMultiprocessDebug()) {
      //noinspection SpellCheckingInspection
      debugParams.addParameter("--multiproc");
    }

    configureCommonDebugParameters(project, debugParams);
  }

  public static void configureCommonDebugParameters(@NotNull Project project,
                                                    @NotNull ParamsGroup debugParams) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      debugParams.addParameter("--DEBUG");
    }

    if (PyDebuggerOptionsProvider.getInstance(project).isSaveCallSignatures()) {
      debugParams.addParameter("--save-signatures");
    }

    if (PyDebuggerOptionsProvider.getInstance(project).isSupportQtDebugging()) {
      String pyQtBackend = StringUtil.toLowerCase(PyDebuggerOptionsProvider.getInstance(project).getPyQtBackend());
      debugParams.addParameter(String.format("--qt-support=%s", pyQtBackend));
    }
  }

  public static void disableBuiltinBreakpoint(@Nullable Sdk sdk, Map<String, String> env) {
    if (sdk != null) {
      final PythonSdkFlavor flavor = PythonSdkFlavor.getFlavor(sdk);
      if (flavor != null) {
        if (flavor.getLanguageLevel(sdk) == LanguageLevel.PYTHON37) {
          env.put("PYTHONBREAKPOINT", "0");
        }
      }
    }
  }

  private static void configureDebugConnectionParameters(@NotNull ParamsGroup debugParams, int serverLocalPort) {
    final String[] debuggerArgs = new String[]{
      CLIENT_PARAM, "127.0.0.1",
      PORT_PARAM, String.valueOf(serverLocalPort),
      FILE_PARAM
    };
    for (String s : debuggerArgs) {
      debugParams.addParameter(s);
    }
  }

  private static void addProjectRootsToEnv(@NotNull Project project, @NotNull Map<String, String> environment) {

    List<String> roots = new ArrayList<>();
    for (VirtualFile contentRoot : ProjectRootManager.getInstance(project).getContentRoots()) {
      roots.add(contentRoot.getPath());
    }

    environment.put(IDE_PROJECT_ROOTS, StringUtil.join(roots, File.pathSeparator));
  }

  private static void addSdkRootsToEnv(@NotNull Map<String, String> environment,
                                       @NotNull AbstractPythonRunConfiguration runConfiguration) {
    final Sdk sdk = runConfiguration.getSdk();
    if (sdk != null) {
      List<String> roots = new ArrayList<>();
      for (VirtualFile contentRoot : sdk.getRootProvider().getFiles(OrderRootType.CLASSES)) {
        roots.add(contentRoot.getPath());
      }
      environment.put(LIBRARY_ROOTS, StringUtil.join(roots, File.pathSeparator));
    }
  }
}
