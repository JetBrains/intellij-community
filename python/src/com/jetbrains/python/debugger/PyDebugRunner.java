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
import com.intellij.execution.target.HostPort;
import com.intellij.execution.target.TargetEnvironment;
import com.intellij.execution.target.TargetEnvironmentRequest;
import com.intellij.execution.target.local.LocalTargetEnvironment;
import com.intellij.execution.target.value.TargetEnvironmentFunctions;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Experiments;
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
import com.jetbrains.python.run.*;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

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

  private static final @NonNls String PYTHONPATH_ENV_NAME = "PYTHONPATH";

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

    if (!Experiments.getInstance().isFeatureEnabled("python.use.targets.api.for.run.configurations")) {
      return createSessionLegacy(state, environment);
    }

    PythonCommandLineState pyState = (PythonCommandLineState)state;

    RunProfile profile = environment.getRunProfile();

    ServerSocket serverSocket = PythonCommandLineState.createServerSocket();
    int serverLocalPort = serverSocket.getLocalPort();
    PythonDebuggerScriptTargetedCommandLineBuilder debuggerScriptCommandLineBuilder =
      new PythonDebuggerScriptTargetedCommandLineBuilder(environment.getProject(), pyState, profile, serverLocalPort);
    ExecutionResult result = pyState.execute(environment.getExecutor(), debuggerScriptCommandLineBuilder);

    return XDebuggerManager.getInstance(environment.getProject()).
      startSession(environment, new XDebugProcessStarter() {
        @Override
        @NotNull
        public XDebugProcess start(@NotNull XDebugSession session) {
          PyDebugProcess pyDebugProcess =
            createDebugProcess(session, serverSocket, result, pyState);

          createConsoleCommunicationAndSetupActions(environment.getProject(), result, pyDebugProcess, session);
          return pyDebugProcess;
        }
      });
  }

  /**
   * To be deprecated.
   * <p>
   * The part of the legacy implementation based on {@link GeneralCommandLine}.
   */
  protected XDebugSession createSessionLegacy(@NotNull RunProfileState state, @NotNull ExecutionEnvironment environment)
    throws ExecutionException {
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

  /**
   * To be deprecated.
   * <p>
   * The part of the legacy implementation based on {@link GeneralCommandLine}.
   */
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

  /**
   * To be deprecated.
   * <p>
   * The part of the legacy implementation based on {@link GeneralCommandLine}.
   */
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

  /**
   * To be deprecated.
   * <p>
   * The part of the legacy implementation based on {@link GeneralCommandLine}.
   */
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

  /**
   * To be deprecated.
   * <p>
   * The part of the legacy implementation based on {@link GeneralCommandLine}.
   */
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

  private @NotNull PythonScriptExecution prepareDebuggerScriptExecution(@NotNull Project project,
                                                                        @NotNull Function<TargetEnvironment, HostPort> serverPortOnTarget,
                                                                        @NotNull PythonCommandLineState pyState,
                                                                        @NotNull PythonExecution originalPythonScript,
                                                                        @Nullable RunProfile runProfile,
                                                                        @NotNull TargetEnvironmentRequest targetEnvironmentRequest) {
    PythonScriptExecution debuggerScript = PythonScripts.prepareHelperScriptExecution(PythonHelper.DEBUGGER, targetEnvironmentRequest);

    PythonScripts.extendEnvs(debuggerScript, originalPythonScript.getEnvs(), targetEnvironmentRequest.getTargetPlatform());

    debuggerScript.setWorkingDir(originalPythonScript.getWorkingDir());

    originalPythonScript.accept(new PythonExecution.Visitor() {
      @Override
      public void visit(@NotNull PythonScriptExecution pythonScriptExecution) {
        // do nothing
      }

      @Override
      public void visit(@NotNull PythonModuleExecution pythonModuleExecution) {
        // add module flag only after command line parameters
        debuggerScript.addParameter(MODULE_PARAM);
      }
    });

    configureDebugParameters(project, pyState, debuggerScript, false);

    // TODO [Targets API] This workaround is required until Cython extensions are uploaded using Targets API
    boolean isLocalTarget = targetEnvironmentRequest instanceof LocalTargetEnvironment;
    configureDebugEnvironment(project, new TargetEnvironmentController(debuggerScript.getEnvs(), targetEnvironmentRequest), runProfile,
                              isLocalTarget);

    configureClientModeDebugConnectionParameters(debuggerScript, serverPortOnTarget);

    originalPythonScript.accept(new PythonExecution.Visitor() {
      @Override
      public void visit(@NotNull PythonScriptExecution pythonScriptExecution) {
        Function<TargetEnvironment, String> scriptPath = pythonScriptExecution.getPythonScriptPath();
        if (scriptPath != null) {
          debuggerScript.addParameter(scriptPath);
        }
        else {
          throw new IllegalArgumentException("Python script path must be set");
        }
      }

      @Override
      public void visit(@NotNull PythonModuleExecution pythonModuleExecution) {
        String moduleName = pythonModuleExecution.getModuleName();
        if (moduleName != null) {
          debuggerScript.addParameter(moduleName);
        }
        else {
          throw new IllegalArgumentException("Python module name must be set");
        }
      }
    });

    debuggerScript.getParameters().addAll(originalPythonScript.getParameters());

    return debuggerScript;
  }

  public static void configureDebugEnvironment(@NotNull Project project, Map<String, String> environment,
                                               @Nullable RunProfile runProfile) {
    configureDebugEnvironment(project, new PlainEnvironmentController(environment), runProfile, true);
  }

  private static void configureDebugEnvironment(@NotNull Project project,
                                                @NotNull EnvironmentController environmentController,
                                                @Nullable RunProfile runProfile,
                                                boolean addCythonExtensionsToPythonPath) {
    if (PyDebuggerOptionsProvider.getInstance(project).isSupportGeventDebugging()) {
      environmentController.putFixedValue(GEVENT_SUPPORT, "True");
    }

    PyDebuggerSettings debuggerSettings = PyDebuggerSettings.getInstance();
    if (debuggerSettings.isSteppingFiltersEnabled()) {
      environmentController.putFixedValue(PYDEVD_FILTERS, debuggerSettings.getSteppingFiltersForProject(project));
    }
    if (debuggerSettings.isLibrariesFilterEnabled()) {
      environmentController.putFixedValue(PYDEVD_FILTER_LIBRARIES, "True");
    }
    if (debuggerSettings.getValuesPolicy() != PyDebugValue.ValuesPolicy.SYNC) {
      environmentController.putFixedValue(PyDebugValue.POLICY_ENV_VARS.get(debuggerSettings.getValuesPolicy()), "True");
    }

    PydevConsoleRunnerFactory.putIPythonEnvFlag(project, environmentController);

    if (addCythonExtensionsToPythonPath) {
      environmentController.appendTargetPathToPathsValue(PYTHONPATH_ENV_NAME, CYTHON_EXTENSIONS_DIR);
    }

    addProjectRootsToEnv(project, environmentController);

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
            environmentController.putFixedValue(PYDEVD_USE_CYTHON, "NO");
          }
        }
      }

      addSdkRootsToEnv(environmentController, runConfiguration);
      environmentController.appendTargetPathToPathsValue(PYTHONPATH_ENV_NAME, runConfiguration.getWorkingDirectorySafe());
    }
  }

  /**
   * To be deprecated.
   * <p>
   * The part of the legacy implementation based on {@link GeneralCommandLine}.
   */
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

  protected void configureDebugParameters(@NotNull Project project,
                                          @NotNull PythonCommandLineState pyState,
                                          @NotNull PythonExecution debuggerScript,
                                          boolean debuggerScriptInServerMode) {
    if (pyState.isMultiprocessDebug() && !debuggerScriptInServerMode) {
      //noinspection SpellCheckingInspection
      debuggerScript.addParameter("--multiproc");
    }

    configureCommonDebugParameters(project, debuggerScript);
  }

  /**
   * To be deprecated.
   * <p>
   * The part of the legacy implementation based on {@link GeneralCommandLine}.
   */
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

  public static void configureCommonDebugParameters(@NotNull Project project,
                                                    @NotNull PythonExecution debuggerScript) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      debuggerScript.addParameter("--DEBUG");
    }

    if (PyDebuggerOptionsProvider.getInstance(project).isSaveCallSignatures()) {
      debuggerScript.addParameter("--save-signatures");
    }

    if (PyDebuggerOptionsProvider.getInstance(project).isSupportQtDebugging()) {
      String pyQtBackend = StringUtil.toLowerCase(PyDebuggerOptionsProvider.getInstance(project).getPyQtBackend());
      debuggerScript.addParameter(String.format("--qt-support=%s", pyQtBackend));
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

  /**
   * To be deprecated.
   * <p>
   * The part of the legacy implementation based on {@link GeneralCommandLine}.
   */
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

  /**
   * Configure the debugger script in <i>client mode</i> to connect to IDE on
   * the execution.
   *
   * @param debuggerScript     the debugger script
   * @param serverPortOnTarget the server
   */
  private static void configureClientModeDebugConnectionParameters(@NotNull PythonExecution debuggerScript,
                                                                   @NotNull Function<TargetEnvironment, HostPort> serverPortOnTarget) {
    // --client
    debuggerScript.addParameter(CLIENT_PARAM);
    debuggerScript.addParameter(serverPortOnTarget.andThen(HostPort::getHost));
    // --port
    debuggerScript.addParameter(PORT_PARAM);
    debuggerScript.addParameter(serverPortOnTarget.andThen(HostPort::getPort).andThen(Object::toString));
    // --file
    debuggerScript.addParameter(FILE_PARAM);
  }

  /**
   * Configure the debugger script in <i>server mode</i> to wait for connection
   * from IDE.
   *
   * @param debuggerScript     the debugger script
   * @param serverPortOnTarget the server
   */
  static void configureServerModeDebugConnectionParameters(@NotNull PythonExecution debuggerScript,
                                                           @NotNull Function<TargetEnvironment, Integer> serverPortOnTarget) {
    // --port
    debuggerScript.addParameter(PORT_PARAM);
    debuggerScript.addParameter(serverPortOnTarget.andThen(Object::toString));
    // --file
    debuggerScript.addParameter(FILE_PARAM);
  }

  private static void addProjectRootsToEnv(@NotNull Project project, @NotNull EnvironmentController environment) {

    List<String> roots = new ArrayList<>();
    for (VirtualFile contentRoot : ProjectRootManager.getInstance(project).getContentRoots()) {
      roots.add(contentRoot.getPath());
    }

    environment.putTargetPathsValue(IDE_PROJECT_ROOTS, roots);
  }

  private static void addSdkRootsToEnv(@NotNull EnvironmentController environmentController,
                                       @NotNull AbstractPythonRunConfiguration runConfiguration) {
    final Sdk sdk = runConfiguration.getSdk();
    if (sdk != null) {
      List<String> roots = new ArrayList<>();
      for (VirtualFile contentRoot : sdk.getRootProvider().getFiles(OrderRootType.CLASSES)) {
        roots.add(contentRoot.getPath());
      }
      // Assume that libraries are located on the target machine
      environmentController.putFixedValue(LIBRARY_ROOTS, StringUtil.join(roots, File.pathSeparator));
    }
  }

  private class PythonDebuggerScriptTargetedCommandLineBuilder implements PythonScriptTargetedCommandLineBuilder {
    private @NotNull final Project myProject;
    private @NotNull final PythonCommandLineState myPyState;
    private @NotNull final RunProfile myProfile;
    private final int myIdeDebugServerLocalPort;

    private PythonDebuggerScriptTargetedCommandLineBuilder(@NotNull Project project,
                                                           @NotNull PythonCommandLineState pyState,
                                                           @NotNull RunProfile profile,
                                                           int ideDebugServerPort) {
      myProject = project;
      myPyState = pyState;
      myProfile = profile;
      myIdeDebugServerLocalPort = ideDebugServerPort;
    }

    @NotNull
    @Override
    public PythonExecution build(@NotNull TargetEnvironmentRequest targetEnvironmentRequest, @NotNull PythonExecution pythonScript) {
      TargetEnvironment.LocalPortBinding ideServerPortBinding = new TargetEnvironment.LocalPortBinding(myIdeDebugServerLocalPort, null);
      targetEnvironmentRequest.getLocalPortBindings().add(ideServerPortBinding);

      Function<TargetEnvironment, HostPort> ideServerPortBindingValue =
        TargetEnvironmentFunctions.getTargetEnvironmentValue(ideServerPortBinding);

      PythonScriptExecution debuggerScript =
        prepareDebuggerScriptExecution(myProject, ideServerPortBindingValue, myPyState, pythonScript, myProfile, targetEnvironmentRequest);

      // TODO [Targets API] We loose interpreter parameters here :(

      PythonSdkFlavor flavor = myPyState.getSdkFlavor();
      List<String> interpreterParameters = new ArrayList<>(myPyState.getConfiguredInterpreterParameters());
      if (flavor != null) {
        interpreterParameters.addAll(flavor.getExtraDebugOptions());
      }

      return debuggerScript;
    }
  }
}
