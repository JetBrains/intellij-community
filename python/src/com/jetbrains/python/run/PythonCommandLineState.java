// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.run;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.configurations.GeneralCommandLine.ParentEnvironmentType;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.filters.UrlFilter;
import com.intellij.execution.impl.ProcessStreamsSynchronizer;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.target.*;
import com.intellij.execution.target.local.LocalTargetEnvironment;
import com.intellij.execution.target.value.TargetEnvironmentFunctions;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.PersistentLibraryKind;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.remote.ProcessControlWithMappings;
import com.intellij.util.PlatformUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.execution.ParametersListUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.console.PyDebugConsoleBuilder;
import com.jetbrains.python.debugger.PyDebugRunner;
import com.jetbrains.python.debugger.PyDebuggerOptionsProvider;
import com.jetbrains.python.facet.LibraryContributingFacet;
import com.jetbrains.python.facet.PythonPathContributingFacet;
import com.jetbrains.python.library.PythonLibraryType;
import com.jetbrains.python.remote.PyRemotePathMapper;
import com.jetbrains.python.run.target.HelpersAwareTargetEnvironmentRequest;
import com.jetbrains.python.run.target.PySdkTargetPaths;
import com.jetbrains.python.run.target.PythonCommandLineTargetEnvironmentProvider;
import com.jetbrains.python.sdk.PySdkUtil;
import com.jetbrains.python.sdk.PythonEnvUtil;
import com.jetbrains.python.sdk.PythonSdkAdditionalData;
import com.jetbrains.python.sdk.PythonSdkUtil;
import com.jetbrains.python.sdk.flavors.JythonSdkFlavor;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.Charset;
import java.util.*;
import java.util.function.Function;

/**
 * Since this state is async, any method could be called on any thread
 *
 * @author traff, Leonid Shalupov
 */
public abstract class PythonCommandLineState extends CommandLineState {
  private final static Logger LOG = Logger.getInstance(PythonCommandLineState.class);

  // command line has a number of fixed groups of parameters; patchers should only operate on them and not the raw list.

  public static final String GROUP_EXE_OPTIONS = "Exe Options";
  public static final String GROUP_DEBUGGER = "Debugger";
  public static final String GROUP_PROFILER = "Profiler";
  public static final String GROUP_COVERAGE = "Coverage";
  /**
   * This group is applied for Python module execution. In this case it
   * contains two parameters: {@code -m} and the module name.
   * <p>
   * For Python script execution this group must be empty.
   * <p>
   * Note that this option <cite>terminates option list</cite> so this group
   * must go <b>after</b> other Python interpreter options. At the same time it
   * must go <b>before</b> <cite>arguments passed to program in
   * sys.argv[1:]</cite>, which are stored in {@link #GROUP_SCRIPT}.
   */
  public static final String GROUP_MODULE = "Module";
  //TODO: DOC ParametersListUtil
  public static final String GROUP_SCRIPT = "Script";
  public static final String MODULE_PARAMETER = "-m";
  private final AbstractPythonRunConfiguration<?> myConfig;

  private Boolean myMultiprocessDebug = null;
  private boolean myRunWithPty = PtyCommandLine.isEnabled();

  public boolean isRunWithPty() {
    return myRunWithPty;
  }

  public boolean isDebug() {
    return PyDebugRunner.PY_DEBUG_RUNNER.equals(getEnvironment().getRunner().getRunnerId());
  }

  public static ServerSocket createServerSocket() throws ExecutionException {
    final ServerSocket serverSocket;
    try {
      //noinspection SocketOpenedButNotSafelyClosed
      serverSocket = new ServerSocket(0);
    }
    catch (IOException e) {
      throw new ExecutionException(PyBundle.message("runcfg.error.message.failed.to.find.free.socket.port"), e);
    }
    return serverSocket;
  }

  public PythonCommandLineState(AbstractPythonRunConfiguration<?> runConfiguration, ExecutionEnvironment env) {
    super(env);
    myConfig = runConfiguration;
  }

  @Nullable
  public PythonSdkFlavor getSdkFlavor() {
    return PythonSdkFlavor.getFlavor(myConfig.getInterpreterPath());
  }

  @Nullable
  public Sdk getSdk() {
    return myConfig.getSdk();
  }

  @NotNull
  @Override
  public ExecutionResult execute(@NotNull Executor executor, @NotNull ProgramRunner<?> runner) throws ExecutionException {
    return execute(executor, (CommandLinePatcher[])null);
  }

  /**
   * <i>To be deprecated. The part of the legacy implementation based on
   * {@link GeneralCommandLine}.</i>
   */
  public ExecutionResult execute(Executor executor, CommandLinePatcher... patchers) throws ExecutionException {
    return execute(executor, getDefaultPythonProcessStarter(), patchers);
  }

  @NotNull
  public ExecutionResult execute(Executor executor) throws ExecutionException {
    return execute(executor, (targetEnvironmentRequest, pythonScript) -> pythonScript);
  }

  @NotNull
  public final List<String> getConfiguredInterpreterParameters() {
    String interpreterOptions = myConfig.getInterpreterOptions();
    if (!StringUtil.isEmptyOrSpaces(interpreterOptions)) {
      return ParametersListUtil.parse(interpreterOptions);
    }
    else {
      return Collections.emptyList();
    }
  }

  /**
   * <i>To be deprecated. The part of the legacy implementation based on
   * {@link GeneralCommandLine}.</i>
   */
  public ExecutionResult execute(Executor executor,
                                 PythonProcessStarter processStarter,
                                 CommandLinePatcher... patchers) throws ExecutionException {
    final ProcessHandler processHandler = startProcess(processStarter, patchers);
    ConsoleView console = createAndAttachConsoleInEDT(myConfig.getProject(), processHandler, executor);
    return new DefaultExecutionResult(console, processHandler, createActions(console, processHandler));
  }

  private @NotNull ConsoleView createAndAttachConsoleInEDT(@NotNull Project project, ProcessHandler processHandler, Executor executor)
    throws ExecutionException {
    final Ref<Object> consoleRef = Ref.create();
    ApplicationManager.getApplication().invokeAndWait(
      () -> {
        try {
          consoleRef.set(createAndAttachConsole(project, processHandler, executor));
        }
        catch (ExecutionException | RuntimeException e) {
          consoleRef.set(e);
        }
      });

    if (consoleRef.get() instanceof ExecutionException) {
      throw (ExecutionException)consoleRef.get();
    }
    else if (consoleRef.get() instanceof RuntimeException) throw (RuntimeException)consoleRef.get();

    return (ConsoleView)consoleRef.get();
  }

  @NotNull
  public ExecutionResult execute(/*TODO @NotNull ?*/Executor executor,
                                                    @NotNull PythonScriptTargetedCommandLineBuilder converter) throws ExecutionException {
    final ProcessHandler processHandler = startProcess(converter);
    final ConsoleView console = createAndAttachConsoleInEDT(myConfig.getProject(), processHandler, executor);
    return new DefaultExecutionResult(console, processHandler, createActions(console, processHandler));
  }

  @NotNull
  protected ConsoleView createAndAttachConsole(Project project, ProcessHandler processHandler, Executor executor)
    throws ExecutionException {
    final ConsoleView consoleView = createConsoleBuilder(project).getConsole();
    consoleView.addMessageFilter(createUrlFilter(processHandler));

    addTracebackFilter(project, consoleView, processHandler);

    consoleView.attachToProcess(processHandler);
    return consoleView;
  }

  protected void addTracebackFilter(Project project, ConsoleView consoleView, ProcessHandler processHandler) {
    // TODO workaround
    if (PythonSdkUtil.isRemote(myConfig.getSdk()) && processHandler instanceof ProcessControlWithMappings) {
      assert processHandler instanceof ProcessControlWithMappings;
      consoleView
        .addMessageFilter(new PyRemoteTracebackFilter(project, myConfig.getWorkingDirectory(), (ProcessControlWithMappings)processHandler));
    }
    else {
      consoleView.addMessageFilter(new PythonTracebackFilter(project, myConfig.getWorkingDirectorySafe()));
    }
    consoleView.addMessageFilter(createUrlFilter(processHandler)); // Url filter is always nice to have
  }

  private TextConsoleBuilder createConsoleBuilder(Project project) {
    if (isDebug()) {
      return new PyDebugConsoleBuilder(project, PythonSdkUtil.findSdkByPath(myConfig.getInterpreterPath()));
    }
    else {
      return TextConsoleBuilderFactory.getInstance().createBuilder(project);
    }
  }

  @Override
  @NotNull
  protected ProcessHandler startProcess() throws ExecutionException {
    return startProcess(getDefaultPythonProcessStarter());
  }

  /**
   * <i>To be deprecated. The part of the legacy implementation based on {@link GeneralCommandLine}.</i>
   * <p>
   * Patches the command line parameters applying patchers from first to last, and then runs it.
   *
   * @param processStarter
   * @param patchers       any number of patchers; any patcher may be null, and the whole argument may be null.
   * @return handler of the started process
   * @throws ExecutionException
   */
  @NotNull
  protected ProcessHandler startProcess(PythonProcessStarter processStarter, CommandLinePatcher... patchers) throws ExecutionException {
    GeneralCommandLine commandLine = generateCommandLine(patchers);

    // Extend command line
    PythonRunConfigurationExtensionsManager.Companion.getInstance()
      .patchCommandLine(myConfig, getRunnerSettings(), commandLine, getEnvironment().getRunner().getRunnerId());

    ProcessHandler processHandler = processStarter.start(myConfig, commandLine);

    // attach extensions
    PythonRunConfigurationExtensionsManager.Companion.getInstance()
      .attachExtensionsToProcess(myConfig, processHandler, getRunnerSettings());

    return processHandler;
  }

  /**
   * Starts the Python script and returns the process handler associated with
   * it.
   * <p>
   * <i>Note that {@code patchCommandLine()} method of
   * {@link PythonRunConfigurationExtensionsManager} cannot be used with
   * {@link TargetedCommandLine} and so it is ignored.</i>
   */
  @NotNull
  protected ProcessHandler startProcess(@NotNull PythonScriptTargetedCommandLineBuilder builder)
    throws ExecutionException {
    HelpersAwareTargetEnvironmentRequest helpersAwareTargetRequest = getPythonTargetInterpreter();

    Sdk sdk = getSdk();
    if (sdk != null) {
      RunConfigurationTargetEnvironmentAdjuster adjuster =
        RunConfigurationTargetEnvironmentAdjuster.findTargetEnvironmentRequestAdjuster(sdk);
      if (adjuster != null) {
        adjuster.adjust(helpersAwareTargetRequest.getTargetEnvironmentRequest(), myConfig);
      }
    }

    // The original Python script to be executed
    PythonExecution pythonScript = buildPythonExecutionFinal(helpersAwareTargetRequest);

    // Python script that may be the debugger script that runs the original script
    PythonExecution realPythonExecution = builder.build(helpersAwareTargetRequest, pythonScript);

    // TODO [Targets API] [major] Meaningful progress indicator should be taken
    EmptyProgressIndicator progressIndicator = new EmptyProgressIndicator();
    TargetEnvironment targetEnvironment =
      helpersAwareTargetRequest.getTargetEnvironmentRequest().prepareEnvironment(TargetProgressIndicator.EMPTY);

    List<String> interpreterParameters = getConfiguredInterpreterParameters();
    TargetedCommandLine targetedCommandLine =
      PythonScripts.buildTargetedCommandLine(realPythonExecution, targetEnvironment, myConfig.getSdk(), interpreterParameters);

    // TODO [Targets API] `myConfig.isPassParentEnvs` must be handled (at least for the local case)
    ProcessHandler processHandler = doStartProcess(targetEnvironment, targetedCommandLine, progressIndicator, isDebug());

    // Attach extensions
    PythonRunConfigurationExtensionsManager.Companion.getInstance()
      .attachExtensionsToProcess(myConfig, processHandler, getRunnerSettings());

    return processHandler;
  }

  @NotNull
  private PythonExecution buildPythonExecutionFinal(HelpersAwareTargetEnvironmentRequest helpersAwareTargetRequest) {
    TargetEnvironmentRequest targetEnvironmentRequest = helpersAwareTargetRequest.getTargetEnvironmentRequest();
    PythonExecution pythonExecution = buildPythonExecution(helpersAwareTargetRequest);
    pythonExecution.setWorkingDir(getPythonExecutionWorkingDir(targetEnvironmentRequest));
    initEnvironment(myConfig.getProject(), pythonExecution, myConfig, createRemotePathMapper(), isDebug(), helpersAwareTargetRequest);
    customizePythonExecutionEnvironmentVars(targetEnvironmentRequest, pythonExecution.getEnvs(), myConfig.isPassParentEnvs());
    PythonScripts.ensureProjectDirIsOnTarget(targetEnvironmentRequest, myConfig.getProject());
    return pythonExecution;
  }

  /**
   * Returns the promise to the working directory path for this
   * {@link PythonCommandLineState}. The working directory is resolved within
   * the uploads that are registered in the provided request.
   *
   * @param targetEnvironment the environment to explore for the working directory upload
   * @return the promise to the working directory path
   */
  protected @Nullable Function<TargetEnvironment, String> getPythonExecutionWorkingDir(@NotNull TargetEnvironmentRequest targetEnvironmentRequest) {
    // the following working directory is located on the local machine
    String workingDir = myConfig.getWorkingDirectory();
    if (!StringUtil.isEmptyOrSpaces(workingDir)) {
      return getTargetPath(targetEnvironmentRequest, workingDir);
    }
    return null;
  }

  /**
   * <i>To be deprecated. The part of the legacy implementation based on
   * {@link GeneralCommandLine}.</i>
   */
  @NotNull
  protected final PythonProcessStarter getDefaultPythonProcessStarter() {
    return (config, commandLine) -> {
      Sdk sdk = PythonSdkUtil.findSdkByPath(myConfig.getInterpreterPath());
      final ProcessHandler processHandler;
      if (PythonSdkUtil.isRemote(sdk)) {
        PyRemotePathMapper pathMapper = createRemotePathMapper();
        processHandler = createRemoteProcessStarter().startRemoteProcess(sdk, commandLine, myConfig.getProject(), pathMapper);
      }
      else {
        EncodingEnvironmentUtil.setLocaleEnvironmentIfMac(commandLine);
        processHandler = doCreateProcess(commandLine);
        ProcessTerminatedListener.attach(processHandler);
      }
      return processHandler;
    };
  }

  @NotNull
  private static ProcessHandler doStartProcess(@NotNull TargetEnvironment targetEnvironment,
                                               @NotNull TargetedCommandLine commandLine,
                                               @NotNull ProgressIndicator progressIndicator,
                                               boolean isDebug) throws ExecutionException {
    final ProcessHandler processHandler;
    processHandler = doCreateProcess(targetEnvironment, commandLine, progressIndicator, isDebug);
    ProcessTerminatedListener.attach(processHandler);
    return processHandler;
  }

  @Nullable
  protected final PyRemotePathMapper createRemotePathMapper() {
    if (myConfig.getMappingSettings() == null) {
      return null;
    }
    else {
      return PyRemotePathMapper.fromSettings(myConfig.getMappingSettings(), PyRemotePathMapper.PyPathMappingType.USER_DEFINED);
    }
  }

  protected PyRemoteProcessStarter createRemoteProcessStarter() {
    return new PyRemoteProcessStarter();
  }

  /**
   * <i>To be deprecated. The part of the legacy implementation based on
   * {@link GeneralCommandLine}.</i>
   * <p>
   * Generate command line and apply patchers.
   *
   * @param patchers array of patchers
   * @return generated command line changed by patchers
   */
  @NotNull
  public final GeneralCommandLine generateCommandLine(CommandLinePatcher @Nullable [] patchers) {
    return applyPatchers(generateCommandLine(), patchers);
  }

  /**
   * <i>To be deprecated. The part of the legacy implementation based on
   * {@link GeneralCommandLine}.</i>
   * <p>
   * Apply patchers to the given command line
   *
   * @param commandLine command line to change
   * @param patchers    array of patchers
   * @return command line changed by patchers
   */
  @NotNull
  private static GeneralCommandLine applyPatchers(@NotNull GeneralCommandLine commandLine, CommandLinePatcher @Nullable [] patchers) {
    if (patchers != null) {
      for (CommandLinePatcher patcher : patchers) {
        if (patcher != null) patcher.patchCommandLine(commandLine);
      }
    }
    return commandLine;
  }

  /**
   * <i>To be deprecated. The part of the legacy implementation based on
   * {@link GeneralCommandLine}.</i>
   */
  protected ProcessHandler doCreateProcess(GeneralCommandLine commandLine) throws ExecutionException {
    return PythonProcessRunner.createProcess(commandLine);
  }

  @NotNull
  private static ProcessHandler doCreateProcess(@NotNull TargetEnvironment targetEnvironment,
                                                @NotNull TargetedCommandLine commandLine,
                                                @NotNull ProgressIndicator progressIndicator,
                                                boolean isDebug) throws ExecutionException {
    Process process = targetEnvironment.createProcess(commandLine, progressIndicator);
    // TODO [Targets API] [major] The command line should be prefixed with the interpreter identifier (f.e. Docker container id)
    String commandLineString = StringUtil.join(commandLine.getCommandPresentation(targetEnvironment), " ");
    if (targetEnvironment instanceof LocalTargetEnvironment) {
      // TODO This special treatment of local target must be replaced with a generalized approach
      //  (f.e. with an ability of a target environment to match arbitrary local path to a target one)
      if (isDebug) {
        return new PyDebugProcessHandler(process, commandLineString, commandLine.getCharset());
      }
      return new PythonProcessHandler(process, commandLineString, commandLine.getCharset());
    }
    return new ProcessHandlerWithPyPositionConverter(process, commandLineString, commandLine.getCharset(), targetEnvironment);
  }

  /**
   * <i>To be deprecated. The part of the legacy implementation based on
   * {@link GeneralCommandLine}.</i>
   * <p>
   * Generate command line from run configuration.
   * It can be overridden if commandline shouldn't be based on the run configuration or when it requires some additional changes
   * before patchers applying.
   *
   * @return generated command line
   */
  @NotNull
  public GeneralCommandLine generateCommandLine() {
    SdkAdditionalData data = null;
    if (myConfig.getSdk() != null) {
      data = myConfig.getSdk().getSdkAdditionalData();
    }
    GeneralCommandLine commandLine = createPythonCommandLine(myConfig.getProject(), data, myConfig, isDebug(), myRunWithPty);

    buildCommandLineParameters(commandLine);

    customizeEnvironmentVars(commandLine.getEnvironment(), myConfig.isPassParentEnvs());

    ProcessStreamsSynchronizer.redirectErrorStreamIfNeeded(commandLine);

    return commandLine;
  }

  /**
   * Builds {@link PythonExecution}.
   * <p>
   * User volumes (including the volumes for project files) are expected to be
   * already requested.
   *
   * @param helpersAwareRequest the request
   * @return the representation of Python script or module execution
   */
  @NotNull
  protected PythonExecution buildPythonExecution(@NotNull HelpersAwareTargetEnvironmentRequest helpersAwareRequest) {
    throw new UnsupportedOperationException("The implementation of Run Configuration based on Targets API is absent");
  }

  /**
   * <i>To be deprecated. The part of the legacy implementation based on
   * {@link GeneralCommandLine}.</i>
   */
  @NotNull
  public static GeneralCommandLine createPythonCommandLine(Project project,
                                                           @Nullable SdkAdditionalData data,
                                                           PythonRunParams config,
                                                           boolean isDebug,
                                                           boolean runWithPty) {
    GeneralCommandLine commandLine = generalCommandLine(runWithPty);

    commandLine.withCharset(EncodingProjectManager.getInstance(project).getDefaultCharset());

    createStandardGroups(commandLine);

    initEnvironment(project, data, commandLine, config, isDebug);

    setRunnerPath(project, commandLine, config);

    return commandLine;
  }

  /**
   * <i>To be deprecated. The part of the legacy implementation based on
   * {@link GeneralCommandLine}.</i>
   */
  private static GeneralCommandLine generalCommandLine(boolean runWithPty) {
    return runWithPty ? new PtyCommandLine().withConsoleMode(false) : new GeneralCommandLine();
  }

  /**
   * Creates a number of parameter groups in the command line:
   * GROUP_EXE_OPTIONS, GROUP_DEBUGGER, GROUP_SCRIPT.
   * These are necessary for command line patchers to work properly.
   *
   * @param commandLine
   */
  public static void createStandardGroups(GeneralCommandLine commandLine) {
    ParametersList params = commandLine.getParametersList();
    params.addParamsGroup(GROUP_EXE_OPTIONS);
    params.addParamsGroup(GROUP_DEBUGGER);
    params.addParamsGroup(GROUP_PROFILER);
    params.addParamsGroup(GROUP_COVERAGE);
    params.addParamsGroup(GROUP_MODULE);
    params.addParamsGroup(GROUP_SCRIPT);
  }

  /**
   * <i>To be deprecated. The part of the legacy implementation based on
   * {@link GeneralCommandLine}.</i>
   */
  protected static void initEnvironment(Project project,
                                        SdkAdditionalData data,
                                        GeneralCommandLine commandLine,
                                        PythonRunParams runParams,
                                        boolean isDebug) {
    Map<String, String> env = Maps.newHashMap();

    setupEncodingEnvs(env, commandLine.getCharset());

    if (runParams.getEnvs() != null) {
      env.putAll(runParams.getEnvs());
    }
    addCommonEnvironmentVariables(getInterpreterPath(project, runParams), env);

    setupVirtualEnvVariables(runParams, env, runParams.getSdkHome());

    commandLine.getEnvironment().clear();
    commandLine.getEnvironment().putAll(env);
    commandLine.withParentEnvironmentType(runParams.isPassParentEnvs() ? ParentEnvironmentType.CONSOLE : ParentEnvironmentType.NONE);

    buildPythonPath(project, commandLine, runParams, isDebug);

    for (PythonCommandLineEnvironmentProvider envProvider : PythonCommandLineEnvironmentProvider.EP_NAME.getExtensionList()) {
      envProvider.extendEnvironment(project, data, commandLine, runParams);
    }
  }

  public static void initEnvironment(@NotNull Project project,
                                     @NotNull PythonExecution commandLine,
                                     @NotNull PythonRunParams runParams,
                                     @NotNull HelpersAwareTargetEnvironmentRequest helpersAwareTargetRequest,
                                     @Nullable PyRemotePathMapper pathMapper) {
    initEnvironment(project, commandLine, runParams, pathMapper, false, helpersAwareTargetRequest);
  }

  /**
   * <i>Note. {@link PythonRunParams#getEnvs()} maps plain {@link String} to
   * {@link String} values and we treat it the same straightforward way.</i>
   */
  private static void initEnvironment(@NotNull Project project,
                                      @NotNull PythonExecution commandLine,
                                      @NotNull PythonRunParams runParams,
                                      @Nullable PyRemotePathMapper pathMapper,
                                      boolean isDebug,
                                      @NotNull HelpersAwareTargetEnvironmentRequest helpersAwareTargetRequest) {
    Map<String, String> env = Maps.newHashMap();

    if (runParams.getEnvs() != null) {
      env.putAll(runParams.getEnvs());
    }
    addCommonEnvironmentVariables(getInterpreterPath(project, runParams), env);

    setupVirtualEnvVariables(runParams, env, runParams.getSdkHome());

    // Carefully patch environment variables
    Map<String, Function<TargetEnvironment, String>> map =
      ContainerUtil.map2Map(env.entrySet(), e -> Pair.create(e.getKey(), TargetEnvironmentFunctions.constant(e.getValue())));
    TargetEnvironmentRequest targetEnvironmentRequest = helpersAwareTargetRequest.getTargetEnvironmentRequest();
    PythonScripts.extendEnvs(commandLine, map, targetEnvironmentRequest.getTargetPlatform());

    Charset charset = commandLine.getCharset();
    if (charset != null) {
      setupEncodingEnvs(commandLine, charset);
    }

    buildPythonPath(project, commandLine, runParams, pathMapper, isDebug, targetEnvironmentRequest);

    for (PythonCommandLineTargetEnvironmentProvider envProvider : PythonCommandLineTargetEnvironmentProvider.EP_NAME.getExtensionList()) {
      envProvider.extendTargetEnvironment(project, helpersAwareTargetRequest, commandLine, runParams);
    }
  }

  private static void setupVirtualEnvVariables(PythonRunParams myConfig, Map<String, String> env, String sdkHome) {
    Sdk sdk = PythonSdkUtil.findSdkByPath(sdkHome);
    if (sdk != null &&
        (Registry.is("python.activate.virtualenv.on.run") && PythonSdkUtil.isVirtualEnv(sdkHome) || PythonSdkUtil.isConda(sdk))) {
      Map<String, String> environment = PySdkUtil.activateVirtualEnv(sdk);
      env.putAll(environment);

      for (Map.Entry<String, String> e : myConfig.getEnvs().entrySet()) {
        if (environment.containsKey(e.getKey())) {
          if ("PATH".equals(e.getKey())) {
            env.put(e.getKey(), PythonEnvUtil.addToPathEnvVar(env.get("PATH"), e.getValue(), true));
          }
          else {
            env.put(e.getKey(), e.getValue());
          }
        }
      }
    }
  }

  protected static void addCommonEnvironmentVariables(@Nullable String homePath, Map<String, String> env) {
    PythonEnvUtil.setPythonUnbuffered(env);
    if (homePath != null) {
      PythonEnvUtil.resetHomePathChanges(homePath, env);
    }
    env.put("PYCHARM_HOSTED", "1");
  }

  // TODO add @NotNull for envs
  public void customizeEnvironmentVars(Map<String, String> envs, boolean passParentEnvs) {
  }

  public void customizePythonExecutionEnvironmentVars(@NotNull TargetEnvironmentRequest targetEnvironment,
                                                      @NotNull Map<String, Function<TargetEnvironment, String>> envs,
                                                      boolean passParentEnvs) {
  }

  /**
   * <i>To be deprecated. The part of the legacy implementation based on
   * {@link GeneralCommandLine}.</i>
   */
  private static void setupEncodingEnvs(Map<String, String> envs, Charset charset) {
    PythonEnvUtil.setupEncodingEnvs(envs, charset);
  }

  /**
   * @see PythonEnvUtil#setupEncodingEnvs(Map, Charset)
   */
  private static void setupEncodingEnvs(@NotNull PythonExecution pythonExecution, @NotNull Charset charset) {
    pythonExecution.addEnvironmentVariable(PythonEnvUtil.PYTHONIOENCODING, charset.name());
  }

  /**
   * <i>To be deprecated. The part of the legacy implementation based on
   * {@link GeneralCommandLine}.</i>
   */
  public static void buildPythonPath(@NotNull Project project,
                                     @NotNull GeneralCommandLine commandLine,
                                     @NotNull PythonRunParams config,
                                     boolean isDebug) {
    Module module = getModule(project, config);
    buildPythonPath(module, commandLine, config.getSdkHome(), config.isPassParentEnvs(), config.shouldAddContentRoots(),
                    config.shouldAddSourceRoots(), isDebug);
  }

  public static void buildPythonPath(@NotNull Project project,
                                     @NotNull PythonExecution pythonScript,
                                     @NotNull PythonRunParams config,
                                     @Nullable PyRemotePathMapper pathMapper,
                                     boolean isDebug,
                                     @NotNull TargetEnvironmentRequest targetEnvironmentRequest) {
    Module module = getModule(project, config);
    buildPythonPath(project, module, pythonScript, config.getSdkHome(), pathMapper, config.isPassParentEnvs(),
                    config.shouldAddContentRoots(), config.shouldAddSourceRoots(), isDebug, targetEnvironmentRequest);
  }

  public static void buildPythonPath(@Nullable Module module,
                                     @NotNull GeneralCommandLine commandLine,
                                     @Nullable String sdkHome,
                                     boolean passParentEnvs,
                                     boolean shouldAddContentRoots,
                                     boolean shouldAddSourceRoots,
                                     boolean isDebug) {
    Sdk pythonSdk = PythonSdkUtil.findSdkByPath(sdkHome);
    if (pythonSdk != null) {
      List<String> pathList = new ArrayList<>();
      pathList.addAll(getAddedPaths(pythonSdk));
      pathList.addAll(collectPythonPath(module, sdkHome, shouldAddContentRoots, shouldAddSourceRoots, isDebug));
      initPythonPath(commandLine, passParentEnvs, pathList, sdkHome);
    }
  }

  public static void buildPythonPath(@NotNull Project project,
                                     @Nullable Module module,
                                     @NotNull PythonExecution pythonScript,
                                     @Nullable String sdkHome,
                                     @Nullable PyRemotePathMapper pathMapper,
                                     boolean passParentEnvs,
                                     boolean shouldAddContentRoots,
                                     boolean shouldAddSourceRoots,
                                     boolean isDebug,
                                     @NotNull TargetEnvironmentRequest targetEnvironmentRequest) {
    Sdk pythonSdk = PythonSdkUtil.findSdkByPath(sdkHome);
    if (pythonSdk != null) {
      List<Function<TargetEnvironment, String>> pathList = new ArrayList<>();
      pathList.addAll(TargetedPythonPaths.getAddedPaths(targetEnvironmentRequest, pythonSdk));
      pathList.addAll(TargetedPythonPaths.collectPythonPath(targetEnvironmentRequest, project, module, sdkHome, pathMapper,
                                                            shouldAddContentRoots, shouldAddSourceRoots, isDebug));
      initPythonPath(pythonScript, passParentEnvs, pathList, sdkHome, targetEnvironmentRequest);
    }
  }

  public static void initPythonPath(@NotNull GeneralCommandLine commandLine,
                                    boolean passParentEnvs,
                                    @NotNull List<String> pathList,
                                    final String interpreterPath) {
    final PythonSdkFlavor flavor = PythonSdkFlavor.getFlavor(interpreterPath);
    if (flavor != null) {
      flavor.initPythonPath(commandLine, passParentEnvs, pathList);
    }
    else {
      PythonEnvUtil.initPythonPath(commandLine.getEnvironment(), passParentEnvs, pathList);
    }
  }

  public static void initPythonPath(@NotNull PythonExecution pythonScript,
                                    boolean passParentEnvs,
                                    @NotNull List<Function<TargetEnvironment, String>> pathList,
                                    @Nullable String interpreterPath,
                                    @NotNull TargetEnvironmentRequest targetEnvironmentRequest) {
    PythonSdkFlavor flavor = PythonSdkFlavor.getFlavor(interpreterPath);
    if (flavor != null) {
      // TODO [Targets API] Take into account Python interpreter flavor when initializing Python path (see PythonSdkFlavor.initPythonPath())
      //  e.g. IRONPYTHONPATH and JYTHONPATH env variables should be used for IronPython and Jython correspondingly
      LOG.warn("Python interpreter flavor is not taken into account while initializing Python path");
    }
    TargetedPythonPaths.initPythonPath(pythonScript.getEnvs(), passParentEnvs, pathList, targetEnvironmentRequest);
  }

  @NotNull
  public static List<String> getAddedPaths(@NotNull Sdk pythonSdk) {
    List<String> pathList = new ArrayList<>();
    final SdkAdditionalData sdkAdditionalData = pythonSdk.getSdkAdditionalData();
    if (sdkAdditionalData instanceof PythonSdkAdditionalData) {
      final Set<VirtualFile> addedPaths = ((PythonSdkAdditionalData)sdkAdditionalData).getAddedPathFiles();
      for (VirtualFile file : addedPaths) {
        addToPythonPath(file, pathList);
      }
    }
    return pathList;
  }

  private static void addToPythonPath(VirtualFile file, Collection<String> pathList) {
    if (file.getFileSystem() instanceof JarFileSystem) {
      final VirtualFile realFile = JarFileSystem.getInstance().getVirtualFileForJar(file);
      if (realFile != null) {
        addIfNeeded(realFile, pathList);
      }
    }
    else {
      addIfNeeded(file, pathList);
    }
  }

  private static void addIfNeeded(@NotNull final VirtualFile file, @NotNull final Collection<String> pathList) {
    addIfNeeded(pathList, file.getPath());
  }

  protected static void addIfNeeded(Collection<String> pathList, String path) {
    final Set<String> vals = Sets.newHashSet(pathList);
    final String filePath = FileUtil.toSystemDependentName(path);
    if (!vals.contains(filePath)) {
      pathList.add(filePath);
    }
  }

  @VisibleForTesting
  public static Collection<String> collectPythonPath(Project project, PythonRunParams config, boolean isDebug) {
    final Module module = getModule(project, config);
    return collectPythonPath(module, config.getSdkHome(), config.shouldAddContentRoots(), config.shouldAddSourceRoots(), isDebug);
  }

  @NotNull
  public static Collection<String> collectPythonPath(@Nullable Module module,
                                                     @Nullable String sdkHome,
                                                     boolean shouldAddContentRoots,
                                                     boolean shouldAddSourceRoots,
                                                     boolean isDebug) {
    final HashSet<String> pythonPath = Sets.newLinkedHashSet(collectPythonPath(module, shouldAddContentRoots, shouldAddSourceRoots));

    if (isDebug && PythonSdkFlavor.getFlavor(sdkHome) instanceof JythonSdkFlavor) {
      //that fixes Jython problem changing sys.argv on execfile, see PY-8164
      pythonPath.add(PythonHelpersLocator.getHelperPath("pycharm"));
      pythonPath.add(PythonHelpersLocator.getHelperPath("pydev"));
    }

    return pythonPath;
  }

  @Nullable
  private static Module getModule(Project project, PythonRunParams config) {
    String name = config.getModuleName();
    return StringUtil.isEmpty(name) ? null : ModuleManager.getInstance(project).findModuleByName(name);
  }

  @NotNull
  public static Collection<String> collectPythonPath(@Nullable Module module) {
    return collectPythonPath(module, true, true);
  }

  @NotNull
  public static Collection<String> collectPythonPath(@Nullable Module module, boolean addContentRoots,
                                                     boolean addSourceRoots) {
    Collection<String> pythonPathList = new LinkedHashSet<>();
    if (module != null) {
      Set<Module> dependencies = new HashSet<>();
      ModuleUtilCore.getDependencies(module, dependencies);

      if (addContentRoots) {
        addRoots(pythonPathList, ModuleRootManager.getInstance(module).getContentRoots());
        for (Module dependency : dependencies) {
          addRoots(pythonPathList, ModuleRootManager.getInstance(dependency).getContentRoots());
        }
      }
      if (addSourceRoots) {
        addRoots(pythonPathList, ModuleRootManager.getInstance(module).getSourceRoots());
        for (Module dependency : dependencies) {
          addRoots(pythonPathList, ModuleRootManager.getInstance(dependency).getSourceRoots());
        }
      }

      addLibrariesFromModule(module, pythonPathList);
      addRootsFromModule(module, pythonPathList);
      for (Module dependency : dependencies) {
        addLibrariesFromModule(dependency, pythonPathList);
        addRootsFromModule(dependency, pythonPathList);
      }
    }
    return pythonPathList;
  }

  private static void addLibrariesFromModule(Module module, Collection<String> list) {
    final OrderEntry[] entries = ModuleRootManager.getInstance(module).getOrderEntries();
    for (OrderEntry entry : entries) {
      if (entry instanceof LibraryOrderEntry) {
        final String name = ((LibraryOrderEntry)entry).getLibraryName();
        if (name != null && name.endsWith(LibraryContributingFacet.PYTHON_FACET_LIBRARY_NAME_SUFFIX)) {
          // skip libraries from Python facet
          continue;
        }
        for (VirtualFile root : ((LibraryOrderEntry)entry).getRootFiles(OrderRootType.CLASSES)) {
          final Library library = ((LibraryOrderEntry)entry).getLibrary();
          if (!PlatformUtils.isPyCharm()) {
            addToPythonPath(root, list);
          }
          else if (library instanceof LibraryEx) {
            final PersistentLibraryKind<?> kind = ((LibraryEx)library).getKind();
            if (kind == PythonLibraryType.getInstance().getKind()) {
              addToPythonPath(root, list);
            }
          }
        }
      }
    }
  }

  private static void addRootsFromModule(Module module, Collection<String> pythonPathList) {

    // for Jython
    final CompilerModuleExtension extension = CompilerModuleExtension.getInstance(module);
    if (extension != null) {
      final VirtualFile path = extension.getCompilerOutputPath();
      if (path != null) {
        pythonPathList.add(path.getPath());
      }
      final VirtualFile pathForTests = extension.getCompilerOutputPathForTests();
      if (pathForTests != null) {
        pythonPathList.add(pathForTests.getPath());
      }
    }

    //additional paths from facets (f.e. buildout)
    final Facet<?>[] facets = FacetManager.getInstance(module).getAllFacets();
    for (Facet<?> facet : facets) {
      if (facet instanceof PythonPathContributingFacet) {
        List<String> more_paths = ((PythonPathContributingFacet)facet).getAdditionalPythonPath();
        if (more_paths != null) pythonPathList.addAll(more_paths);
      }
    }
  }

  private static void addRoots(Collection<String> pythonPathList, VirtualFile[] roots) {
    for (VirtualFile root : roots) {
      addToPythonPath(root, pythonPathList);
    }
  }

  /**
   * <i>To be deprecated. The part of the legacy implementation based on
   * {@link GeneralCommandLine}.</i>
   */
  protected static void setRunnerPath(Project project, GeneralCommandLine commandLine, PythonRunParams config) {
    String interpreterPath = getInterpreterPath(project, config);
    if (StringUtil.isNotEmpty(interpreterPath)) {
      commandLine.setExePath(FileUtil.toSystemDependentName(interpreterPath));
    }
  }

  @Nullable
  public static String getInterpreterPath(Project project, PythonRunParams config) {
    String sdkHome = config.getSdkHome();
    if (config.isUseModuleSdk() || StringUtil.isEmpty(sdkHome)) {
      Module module = getModule(project, config);

      Sdk sdk = PythonSdkUtil.findPythonSdk(module);

      if (sdk != null) {
        sdkHome = sdk.getHomePath();
      }
    }

    return sdkHome;
  }

  protected String getInterpreterPath() throws ExecutionException {
    String interpreterPath = myConfig.getInterpreterPath();
    if (interpreterPath == null) {
      throw new ExecutionException(PyBundle.message("runcfg.error.message.cannot.find.python.interpreter"));
    }
    return interpreterPath;
  }

  @NotNull
  private HelpersAwareTargetEnvironmentRequest getPythonTargetInterpreter() {
    return getPythonTargetInterpreter(myConfig.getProject(), getSdk());
  }

  @NotNull
  public static HelpersAwareTargetEnvironmentRequest getPythonTargetInterpreter(@NotNull Project project, @Nullable Sdk sdk) {
    if (sdk == null) {
      throw new IllegalStateException("SDK is not defined for Run Configuration");
    }
    else {
      HelpersAwareTargetEnvironmentRequest helpersAwareTargetRequest =
        PythonInterpreterTargetEnvironmentFactory.findPythonTargetInterpreter(sdk, project);
      if (helpersAwareTargetRequest == null) {
        throw new IllegalStateException("Cannot find execution environment for SDK " + sdk);
      }
      return helpersAwareTargetRequest;
    }
  }

  /**
   * <i>To be deprecated. The part of the legacy implementation based on
   * {@link GeneralCommandLine}.</i>
   */
  protected void buildCommandLineParameters(GeneralCommandLine commandLine) {
  }

  public boolean isMultiprocessDebug() {
    if (myMultiprocessDebug != null) {
      return myMultiprocessDebug;
    }
    else {
      return PyDebuggerOptionsProvider.getInstance(myConfig.getProject()).isAttachToSubprocess();
    }
  }

  public void setMultiprocessDebug(boolean multiprocessDebug) {
    myMultiprocessDebug = multiprocessDebug;
  }

  public void setRunWithPty(boolean runWithPty) {
    myRunWithPty = runWithPty;
  }

  @NotNull
  protected UrlFilter createUrlFilter(ProcessHandler handler) {
    return new UrlFilter();
  }

  @NotNull
  protected Function<TargetEnvironment, String> getTargetPath(@NotNull TargetEnvironmentRequest targetEnvironmentRequest,
                                                              @NotNull String scriptPath) {
    return PySdkTargetPaths.getTargetPathForPythonScriptExecution(targetEnvironmentRequest, myConfig.getProject(), myConfig.getSdk(),
                                                                  createRemotePathMapper(), scriptPath);
  }

  /**
   * <i>To be deprecated. The part of the legacy implementation based on
   * {@link GeneralCommandLine}.</i>
   */
  public interface PythonProcessStarter {
    @NotNull
    ProcessHandler start(@NotNull AbstractPythonRunConfiguration<?> config,
                         @NotNull GeneralCommandLine commandLine) throws ExecutionException;
  }
}
