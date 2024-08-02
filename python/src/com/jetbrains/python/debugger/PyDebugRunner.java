// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger;

import com.intellij.codeWithMe.ClientId;
import com.intellij.debugger.ui.DebuggerContentInfo;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.console.LanguageConsoleBuilder;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.target.HostPort;
import com.intellij.execution.target.ResolvedPortBinding;
import com.intellij.execution.target.TargetEnvironment;
import com.intellij.execution.target.TargetEnvironmentRequest;
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest;
import com.intellij.execution.target.value.TargetEnvironmentFunctions;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.layout.LayoutAttractionPolicy;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.AppUIExecutor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ExperimentalUI;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.net.NetUtils;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugProcessStarter;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PythonHelper;
import com.jetbrains.python.console.*;
import com.jetbrains.python.console.pydev.ConsoleCommunicationListener;
import com.jetbrains.python.debugger.settings.PyDebuggerSettings;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.run.*;
import com.jetbrains.python.run.target.HelpersAwareTargetEnvironmentRequest;
import com.jetbrains.python.sdk.PySdkExtKt;
import com.jetbrains.python.sdk.PythonSdkUtil;
import com.jetbrains.python.sdk.flavors.CPythonSdkFlavor;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.jetbrains.python.actions.PyExecuteInConsole.requestFocus;
import static com.jetbrains.python.actions.PyExecuteInConsole.selectConsoleTab;
import static com.jetbrains.python.inspections.PyInterpreterInspection.InterpreterSettingsQuickFix.showPythonInterpreterSettings;


public class PyDebugRunner implements ProgramRunner<RunnerSettings> {
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
  public static final @NonNls String GEVENT_SUPPORT = "GEVENT_SUPPORT";
  public static final @NonNls String PYDEVD_FILTERS = "PYDEVD_FILTERS";
  public static final @NonNls String PYDEVD_FILTER_LIBRARIES = "PYDEVD_FILTER_LIBRARIES";
  public static final @NonNls String CYTHON_EXTENSIONS_DIR = new File(PathManager.getSystemPath(), "cythonExtensions").toString();

  protected static final @NonNls String PYDEVD_USE_CYTHON = "PYDEVD_USE_CYTHON";
  protected static final @NonNls String PYCHARM_DEBUG = "PYCHARM_DEBUG";
  protected static final @NonNls String USE_LOW_IMPACT_MONITORING = "USE_LOW_IMPACT_MONITORING";
  protected static final @NonNls String HALT_VARIABLE_RESOLVE_THREADS_ON_STEP_RESUME = "HALT_VARIABLE_RESOLVE_THREADS_ON_STEP_RESUME";
  protected static final @NonNls String SINGLE_PORT_MODE = "SINGLE_PORT_MODE";

  @SuppressWarnings("SpellCheckingInspection")
  private static final @NonNls String PYTHONPATH_ENV_NAME = "PYTHONPATH";

  private static final @NonNls String PYTHON_DONT_WRITE_PYC_FLAG = "-B";

  private static final @NonNls String PYTHON3_PYCACHE_PREFIX_OPTION = "pycache_prefix=";

  private static final Logger LOG = Logger.getInstance(PyDebugRunner.class);

  @Override
  public @NotNull String getRunnerId() {
    return PY_DEBUG_RUNNER;
  }

  @Override
  public boolean canRun(final @NotNull String executorId, final @NotNull RunProfile profile) {
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

  private static boolean isDebuggable(final @NotNull RunProfile profile) {
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

  @RequiresEdt
  protected Promise<@NotNull XDebugSession> createSession(@NotNull RunProfileState state, final @NotNull ExecutionEnvironment environment) {
    FileDocumentManager.getInstance().saveAllDocuments();
    if (Registry.is("python.use.targets.api")) {
      return createSessionUsingTargetsApi(state, environment);
    }
    if (PyRunnerUtil.isTargetBasedSdkAssigned(state)) {
      Project project = environment.getProject();
      Module module = PyRunnerUtil.getModule(state);
      throw new RuntimeExceptionWithHyperlink(PyBundle.message("runcfg.error.message.python.interpreter.is.invalid.configure"),
                                              () -> showPythonInterpreterSettings(project, module));
    }
    return createSessionLegacy(state, environment);
  }

  private @NotNull Promise<XDebugSession> createSessionUsingTargetsApi(@NotNull RunProfileState state,
                                                                       final @NotNull ExecutionEnvironment environment) {
    PythonCommandLineState pyState = (PythonCommandLineState)state;
    RunProfile profile = environment.getRunProfile();
    var clientId = ClientId.getCurrentOrNull();
    return Promises
      .runAsync(() -> {
        int serverLocalPort = findAvailableSocketPort();
        try {
          PythonDebuggerScriptTargetedCommandLineBuilder debuggerScriptCommandLineBuilder =
            new PythonDebuggerScriptTargetedCommandLineBuilder(environment.getProject(), pyState, profile, serverLocalPort);
          ExecutionResult result = pyState.execute(environment.getExecutor(), debuggerScriptCommandLineBuilder);
          ServerSocket serverSocket = debuggerScriptCommandLineBuilder.getServerSocketForDebugging();
          if (serverSocket == null) {
            LOG.error("The server socket has not been created after the target environment preparation" +
                      ", trying to fallback and create the server socket on the loopback address");
            serverSocket = createServerSocketOnLoopbackAddress(serverLocalPort);
          }
          return Pair.create(serverSocket, result);
        }
        catch (ExecutionException err) {
          throw new RuntimeException(err.getMessage(), err);
        }
      })
      .thenAsync(pair -> AppUIExecutor.onUiThread().submit(() -> {
        try (AccessToken ignored = ClientId.withClientId(clientId)) {
          ServerSocket serverSocket = pair.getFirst();
          ExecutionResult result = pair.getSecond();
          return createXDebugSession(environment, pyState, serverSocket, result);
        }
      }));
  }

  private static @NotNull ServerSocket createServerSocketOnLoopbackAddress(int serverLocalPort) {
    try {
      return new ServerSocket(serverLocalPort, 0, InetAddress.getLoopbackAddress());
    }
    catch (IOException e) {
      throw new RuntimeException(e.getMessage(), e);
    }
  }

  public static int findAvailableSocketPort() {
    try {
      return NetUtils.findAvailableSocketPort();
    }
    catch (IOException e) {
      throw new RuntimeException(PyBundle.message("runcfg.error.message.failed.to.find.free.socket.port"), e);
    }
  }

  private @NotNull XDebugSession createXDebugSession(@NotNull ExecutionEnvironment environment,
                                                     PythonCommandLineState pyState,
                                                     ServerSocket serverSocket, ExecutionResult result) throws ExecutionException {
    XDebugSession session = XDebuggerManager.getInstance(environment.getProject()).
      startSession(environment, new XDebugProcessStarter() {
        @Override
        public @NotNull XDebugProcess start(final @NotNull XDebugSession session) {
          PyDebugProcess pyDebugProcess = createDebugProcess(session, serverSocket, result, pyState);

          createConsoleCommunicationAndSetupActions(environment.getProject(), result, pyDebugProcess, session);
          return pyDebugProcess;
        }
      });

    if (ExperimentalUI.isNewUI()) {
      session.getUI().getDefaults().initContentAttraction(DebuggerContentInfo.CONSOLE_CONTENT,
                                                          XDebuggerUIConstants.LAYOUT_VIEW_FINISH_CONDITION,
                                                          new LayoutAttractionPolicy.FocusOnce());
    }
    return session;
  }

  /**
   * To be deprecated.
   * <p>
   * The part of the legacy implementation based on {@link GeneralCommandLine}.
   */
  private @NotNull Promise<XDebugSession> createSessionLegacy(@NotNull RunProfileState state, @NotNull ExecutionEnvironment environment) {
    final PythonCommandLineState pyState = (PythonCommandLineState)state;
    final RunProfile profile = environment.getRunProfile();

    Sdk sdk = pyState.getSdk();
    PyDebugSessionFactory sessionCreator = PyDebugSessionFactory.findExtension(sdk);
    if (sessionCreator != null) {
      return AppUIExecutor.onWriteThread().submit(() -> sessionCreator.createSession(pyState, environment));
    }

    return Promises
      .runAsync(() -> {
        try {
          final ServerSocket serverSocket = PythonCommandLineState.createServerSocket();
          final int serverLocalPort = serverSocket.getLocalPort();
          final ExecutionResult result = pyState
            .execute(environment.getExecutor(), createCommandLinePatchers(environment.getProject(), pyState, profile, serverLocalPort));
          return Pair.create(serverSocket, result);
        }
        catch (ExecutionException err) {
          throw new RuntimeException(err.getMessage(), err);
        }
      })
      .thenAsync(pair -> AppUIExecutor.onUiThread().submit(() -> {
        ServerSocket serverSocket = pair.getFirst();
        ExecutionResult result = pair.getSecond();
        return createXDebugSession(environment, pyState, serverSocket, result);
      }));
  }

  protected @NotNull PyDebugProcess createDebugProcess(@NotNull XDebugSession session,
                                                       ServerSocket serverSocket,
                                                       ExecutionResult result,
                                                       PythonCommandLineState pyState) {
    return new PyDebugProcess(session, serverSocket, result.getExecutionConsole(), result.getProcessHandler(),
                              pyState.isMultiprocessDebug());
  }

  /**
   * Calling this method with an SSH interpreter would lead to an error, because this method is expected to be executed from EDT,
   * and therefore it would try to create an SSH connection in EDT.
   *
   * @deprecated Override {@link #execute(ExecutionEnvironment, RunProfileState)} instead.
   */
  @Deprecated(forRemoval = true)
  protected @NotNull RunContentDescriptor doExecute(@NotNull RunProfileState state, final @NotNull ExecutionEnvironment environment)
    throws ExecutionException {
    // The PyDebugRunner class might inherit AsyncProgramRunner, but some magic is added for backward compatibility
    // with GenericProgramRunner. Consider changing the base class when doExecute method is deleted.
    // Now the logic here is the same as in execute() except the fact that everything is called in EDT.
    FileDocumentManager.getInstance().saveAllDocuments();

    final PythonCommandLineState pyState = (PythonCommandLineState)state;
    Sdk sdk = pyState.getSdk();
    PyDebugSessionFactory sessionCreator = PyDebugSessionFactory.findExtension(sdk);
    final XDebugSession session;
    if (sessionCreator != null) {
      session = sessionCreator.createSession(pyState, environment);
    }
    else {
      final ServerSocket serverSocket = PythonCommandLineState.createServerSocket();
      final int serverLocalPort = serverSocket.getLocalPort();
      RunProfile profile = environment.getRunProfile();
      final ExecutionResult result =
        pyState.execute(environment.getExecutor(), createCommandLinePatchers(environment.getProject(), pyState, profile, serverLocalPort));

      session = createXDebugSession(environment, pyState, serverSocket, result);
    }
    initSession(session, state, environment.getExecutor());
    return session.getRunContentDescriptor();
  }

  /**
   * The same signature and the same meaning as in
   * {@link com.intellij.execution.runners.AsyncProgramRunner#execute(ExecutionEnvironment, RunProfileState)}.
   */
  protected @NotNull Promise<@Nullable RunContentDescriptor> execute(@NotNull ExecutionEnvironment environment, @NotNull RunProfileState state)
    throws ExecutionException {
    return createSession(state, environment)
      .thenAsync(session -> AppUIExecutor.onUiThread().submit(() -> {
        initSession(session, state, environment.getExecutor());
        return session.getRunContentDescriptor();
      }));
  }

  @Override
  public void execute(@NotNull ExecutionEnvironment environment) throws ExecutionException {
    var state = environment.getState();
    if (state != null) {
      ExecutionManager.getInstance(environment.getProject()).startRunProfile(environment, () -> {
        try {
          return executeWithLegacyWorkaround(environment, state);
        }
        catch (ExecutionException e) {
          throw new RuntimeException(e.getMessage(), e);
        }
      });
    }
  }

  private @NotNull Promise<@Nullable RunContentDescriptor> executeWithLegacyWorkaround(
    @NotNull ExecutionEnvironment environment,
    @NotNull RunProfileState state
  ) throws ExecutionException {
    boolean callExecute = true;
    try {
      callExecute = getClass()
        .getDeclaredMethod("doExecute", RunProfileState.class, ExecutionEnvironment.class)
        .getDeclaringClass()
        .equals(PyDebugRunner.class);
    }
    catch (NoSuchMethodException e) {
      // It's not supposed to happen, but if it happens, asynchronous execute is called.
    }
    if (callExecute) {
      return execute(environment, state);
    }
    return AppUIExecutor.onUiThread().submit(() -> doExecute(state, environment));
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

  public static void createConsoleCommunicationAndSetupActions(final @NotNull Project project,
                                                               final @NotNull ExecutionResult result,
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


    PydevDebugConsoleExecuteActionHandler consoleExecuteActionHandler = new PydevDebugConsoleExecuteActionHandler(console,
                                                                                                                  processHandler,
                                                                                                                  debugConsoleCommunication);

    PythonDebugConsoleCommunication pythonDebugConsoleCommunication =
      initDebugConsoleView(pythonConsoleView, consoleExecuteActionHandler, debugProcess, processHandler, debugConsoleCommunication, session);

    // We need to enable Debug Console one more time after adding the consoleExecuteActionHandler to the pythonConsoleView
    if (console.isEnabled()) {
      console.enableConsole(false);
    }

    return pythonDebugConsoleCommunication;
  }

  protected static PythonDebugConsoleCommunication initDebugConsoleView(@NotNull PythonConsoleView pythonConsoleView,
                                                                        @NotNull PydevDebugConsoleExecuteActionHandler consoleExecuteActionHandler,
                                                                        @NotNull PyDebugProcess debugProcess,
                                                                        @NotNull ProcessHandler processHandler,
                                                                        @NotNull PythonDebugConsoleCommunication debugConsoleCommunication,
                                                                        final XDebugSession session) {
    pythonConsoleView.setExecutionHandler(consoleExecuteActionHandler);

    debugProcess.getSession().addSessionListener(consoleExecuteActionHandler);
    new LanguageConsoleBuilder(pythonConsoleView).processHandler(processHandler).initActions(consoleExecuteActionHandler, "py", true);


    debugConsoleCommunication.addCommunicationListener(new ConsoleCommunicationListener() {
      @Override
      public void commandExecuted(boolean more) {
        session.rebuildViews();
      }

      @Override
      public void inputRequested() {
        ApplicationManager.getApplication().invokeLater(() -> {
          if (session.getConsoleView() instanceof PythonDebugLanguageConsoleView debugConsoleView) {
            selectConsoleTab(session.getRunContentDescriptor(), session.getUI().getContentManager(), true);

            if (pythonConsoleView.isVisible()) {
              requestFocus(true, null, pythonConsoleView, true);
            }
            else if (debugConsoleView.getPrimaryConsoleView() instanceof ConsoleViewImpl consoleView) {
              requestFocus(false, consoleView.getEditor(), null, true);
            }
          }
        });
      }
    });

    return debugConsoleCommunication;
  }

  /**
   * To be deprecated.
   * <p>
   * The part of the legacy implementation based on {@link GeneralCommandLine}.
   */
  public static @Nullable CommandLinePatcher createRunConfigPatcher(RunProfileState state, RunProfile profile) {
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

        final PythonSdkFlavor<?> flavor = pyState.getSdkFlavor();
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
                                                                        @NotNull HelpersAwareTargetEnvironmentRequest request) {
    PythonScriptExecution debuggerScript = PythonScripts.prepareHelperScriptExecution(PythonHelper.DEBUGGER, request);

    TargetEnvironmentRequest targetEnvironmentRequest = request.getTargetEnvironmentRequest();
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
    boolean isLocalTarget = targetEnvironmentRequest instanceof LocalTargetEnvironmentRequest;
    configureDebugEnvironment(project, new TargetEnvironmentController(debuggerScript.getEnvs(), request), runProfile,
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
    if (debuggerSettings.getValuesPolicy() != ValuesPolicy.SYNC) {
      environmentController.putFixedValue(PyDebugValue.POLICY_ENV_VARS.get(debuggerSettings.getValuesPolicy()), "True");
    }

    PydevConsoleRunnerFactory.putIPythonEnvFlag(project, environmentController);

    if (addCythonExtensionsToPythonPath) {
      environmentController.appendTargetPathToPathsValue(PYTHONPATH_ENV_NAME, CYTHON_EXTENSIONS_DIR);
    }

    PyDebugAsyncioCustomizer.Companion.getInstance().enableAsyncioMode(environmentController);

    AbstractPythonRunConfiguration<?> runConfiguration = runProfile instanceof AbstractPythonRunConfiguration<?> ?
                                                               (AbstractPythonRunConfiguration<?>)runProfile : null;
    Module module = runConfiguration != null ? runConfiguration.getModule() : null;

    if (module != null) {
      addProjectRootsToEnv(module, environmentController);
    }

    if (runConfiguration != null) {
      final Sdk sdk = runConfiguration.getSdk();
      if (sdk != null) {
        final PythonSdkFlavor<?> flavor = PythonSdkFlavor.getFlavor(sdk);
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

    applyRegistryFlags(environmentController);
  }

  private static void applyRegistryFlags(@NotNull EnvironmentController environmentController) {
    var registryManager = RegistryManager.getInstance();

    if (registryManager.is("python.debug.low.impact.monitoring.api")) {
      environmentController.putFixedValue(USE_LOW_IMPACT_MONITORING, "True");
    }

    if (!registryManager.is("python.debug.enable.cython.speedups")) {
      environmentController.putFixedValue(PYDEVD_USE_CYTHON, "NO");
    }

    if (registryManager.is("python.debug.enable.diagnostic.prints")) {
      environmentController.putFixedValue(PYCHARM_DEBUG, "True");
    }

    if (registryManager.is("python.debug.halt.variable.resolve.threads.on.step.resume")) {
      environmentController.putFixedValue(HALT_VARIABLE_RESOLVE_THREADS_ON_STEP_RESUME, "True");
    }

    if (registryManager.is("python.debug.use.single.port")) {
      environmentController.putFixedValue(SINGLE_PORT_MODE, "True");
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
      debugParams.addParameter(getMultiprocessDebugParameter());
    }

    configureCommonDebugParameters(project, debugParams);
  }

  protected void configureDebugParameters(@NotNull Project project,
                                          @NotNull PythonCommandLineState pyState,
                                          @NotNull PythonExecution debuggerScript,
                                          boolean debuggerScriptInServerMode) {
    if (pyState instanceof PythonScriptCommandLineState && ((PythonScriptCommandLineState)pyState).showCommandLineAfterwards()) {
      debuggerScript.addParameter("--cmd-line");
    }

    if (pyState.isMultiprocessDebug() && !debuggerScriptInServerMode) {
      debuggerScript.addParameter(getMultiprocessDebugParameter());
    }

    configureCommonDebugParameters(project, debuggerScript);
  }

  /**
   * @deprecated the dispatcher code must be removed if no issues arise in Python debugger and this method must be replaced with
   * {@link #MULTIPROCESS_PARAM} constant.
   */
  @Deprecated(forRemoval = true)
  private static @NotNull String getMultiprocessDebugParameter() {
    if (Registry.get("python.debugger.use.dispatcher").asBoolean()) {
      return "--multiproc";
    }
    else {
      return "--multiprocess";
    }
  }

  /**
   * To be deprecated.
   * <p>
   * The part of the legacy implementation based on {@link GeneralCommandLine}.
   */
  public static void configureCommonDebugParameters(@NotNull Project project,
                                                    @NotNull ParamsGroup debugParams) {
    if (ApplicationManager.getApplication().isUnitTestMode() && !isForceDisableDebuggerTracing()) {
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
    if (ApplicationManager.getApplication().isUnitTestMode() && !isForceDisableDebuggerTracing()) {
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

  /**
   * A hack for disabling the debugging tracing in the unit-test mode.
   */
  public static final Key<Boolean> FORCE_DISABLE_DEBUGGER_TRACING = Key.create("FORCE_DISABLE_DEBUGGER_TRACING");

  private static boolean isForceDisableDebuggerTracing() {
    return Boolean.TRUE.equals(ApplicationManager.getApplication().getUserData(FORCE_DISABLE_DEBUGGER_TRACING));
  }

  /**
   * To be deprecated.
   * <p>
   * The part of the legacy implementation based on {@link GeneralCommandLine}.
   */
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
    final String[] debuggerArgs = {
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

  private static void addProjectRootsToEnv(@NotNull Module module, @NotNull EnvironmentController environment) {
    final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
    Stream<VirtualFile> contentRoots = Stream.concat(Arrays.stream(moduleRootManager.getContentRoots()),
                                                     getDependenciesContentRoots(module));

    environment.putTargetPathsValue(IDE_PROJECT_ROOTS, contentRoots.map(contentRoot -> contentRoot.getPath()).collect(Collectors.toList()));
  }

  private static Stream<VirtualFile> getDependenciesContentRoots(Module module) {
    final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
    final Stream<Module> dependencies = Arrays.stream(moduleRootManager.getDependencies());
    return dependencies.flatMap(d -> Arrays.stream(ModuleRootManager.getInstance(d).getContentRoots()));
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
    private final @NotNull Project myProject;
    private final @NotNull PythonCommandLineState myPyState;
    private final @NotNull RunProfile myProfile;
    private final int myIdeDebugServerLocalPort;
    private volatile @Nullable ServerSocket myServerSocketForDebugging;

    private PythonDebuggerScriptTargetedCommandLineBuilder(@NotNull Project project,
                                                           @NotNull PythonCommandLineState pyState,
                                                           @NotNull RunProfile profile,
                                                           int ideDebugServerPort) {
      myProject = project;
      myPyState = pyState;
      myProfile = profile;
      myIdeDebugServerLocalPort = ideDebugServerPort;
    }

    @Override
    public @NotNull PythonExecution build(@NotNull HelpersAwareTargetEnvironmentRequest helpersAwareTargetRequest,
                                          @NotNull PythonExecution pythonScript) {
      TargetEnvironment.LocalPortBinding ideServerPortBinding = new TargetEnvironment.LocalPortBinding(myIdeDebugServerLocalPort, null);
      helpersAwareTargetRequest.getTargetEnvironmentRequest().getLocalPortBindings().add(ideServerPortBinding);

      helpersAwareTargetRequest.getTargetEnvironmentRequest().onEnvironmentPrepared((environment, indicator) -> {
        try {
          myServerSocketForDebugging = createServerSocketForDebugging(environment, ideServerPortBinding);
        }
        catch (IOException e) {
          LOG.error("Unable to create server socket for debugging", e);
        }
        return null;
      });

      Function<TargetEnvironment, HostPort> ideServerPortBindingValue =
        TargetEnvironmentFunctions.getTargetEnvironmentValue(ideServerPortBinding);

      PythonScriptExecution debuggerScript =
        prepareDebuggerScriptExecution(myProject, ideServerPortBindingValue, myPyState, pythonScript, myProfile,
                                       helpersAwareTargetRequest);

      var configuredInterpreterParameters = myPyState.getConfiguredInterpreterParameters();

      var flavor = myPyState.getSdkFlavor();
      if (flavor != null) {
        debuggerScript.getAdditionalInterpreterParameters().addAll(flavor.getExtraDebugOptions());
      }

      debuggerScript.getAdditionalInterpreterParameters().addAll(
        createInterpreterParametersToPreventPycGenerationInHelpersDir(configuredInterpreterParameters));

      debuggerScript.setCharset(PydevConsoleRunnerImpl.CONSOLE_CHARSET);

      return debuggerScript;
    }

    private List<String> createInterpreterParametersToPreventPycGenerationInHelpersDir(@NotNull List<String>
                                                                                         existingInterpreterParameters) {
      var sdk = myPyState.getSdk();

      if (sdk == null || PythonSdkUtil.isRemote(sdk)) {
        return Collections.emptyList();
      }

      var pythonVersion = sdk.getVersionString();

      if (pythonVersion == null) {
        return Collections.emptyList();
      }

      var pythonSdkFlavor = PySdkExtKt.getOrCreateAdditionalData(sdk).getFlavor();

      if (!(pythonSdkFlavor instanceof CPythonSdkFlavor)) {
        return Collections.emptyList();
      }

      // Note, that we don't modify the parameters if the options are already set by the user.

      if (existingInterpreterParameters.contains(PYTHON_DONT_WRITE_PYC_FLAG)) {
        return Collections.emptyList();
      }

      if (pythonSdkFlavor.getLanguageLevel(sdk).isOlderThan(LanguageLevel.PYTHON38)) {
        // There is no option for defining a custom directory for .pyc files in Python 3.7 and older, thus disable cache generation entirely.
        return List.of(PYTHON_DONT_WRITE_PYC_FLAG);
      }
      else {
        for (int i = 0; i < existingInterpreterParameters.size(); i++) {
          if (existingInterpreterParameters.get(i).startsWith(PYTHON3_PYCACHE_PREFIX_OPTION)
              && i > 0 && existingInterpreterParameters.get(i - 1).equals("-X")) {
            return Collections.emptyList();
          }
        }
        try {
          return List.of( "-X", PYTHON3_PYCACHE_PREFIX_OPTION + prepareAndGetPycacheDirectory());
        }
        catch (IOException e) {
          return List.of(PYTHON_DONT_WRITE_PYC_FLAG);
        }
      }
    }

    private static Path prepareAndGetPycacheDirectory() throws IOException {
      var pycacheDir = PathManager.getSystemDir().resolve("cpython-cache");
      Files.createDirectories(pycacheDir);
      return pycacheDir.toAbsolutePath();
    }

    private static @NotNull ServerSocket createServerSocketForDebugging(@NotNull TargetEnvironment environment,
                                                                        @NotNull TargetEnvironment.LocalPortBinding ideServerPortBinding)
      throws IOException {
      ResolvedPortBinding localPortBinding = environment.getLocalPortBindings().get(ideServerPortBinding);
      int port = ideServerPortBinding.getLocal();
      InetAddress hostInetAddress;
      if (localPortBinding != null) {
        hostInetAddress = InetAddress.getByName(localPortBinding.getLocalEndpoint().getHost());
      }
      else {
        LOG.error("The resolution of the local port binding for \"" + port + "\" port cannot be found in the prepared environment" +
                  ", falling back to \"localhost\" for the server socket binding on the local machine");
        hostInetAddress = InetAddress.getLoopbackAddress();
      }
      LOG.debug("Creating server socket for debugging at " + hostInetAddress + ":" + port);
      return new ServerSocket(port, 0, hostInetAddress);
    }

    /**
     * Returns the server socket allocated after creation of the environment and before starting the process.
     *
     * @return the allocated server socket or {@code null} if the allocation failed
     */
    public @Nullable ServerSocket getServerSocketForDebugging() {
      return myServerSocketForDebugging;
    }
  }
}
