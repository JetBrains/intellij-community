// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.CommandLineState;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.GeneralCommandLine.ParentEnvironmentType;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.configurations.PtyCommandLine;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.filters.UrlFilter;
import com.intellij.execution.impl.ProcessStreamsSynchronizer;
import com.intellij.execution.process.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.target.*;
import com.intellij.execution.target.local.LocalTargetEnvironment;
import com.intellij.execution.target.value.TargetEnvironmentFunctions;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.openapi.application.ApplicationManager;
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
import com.intellij.remote.RemoteSdkProperties;
import com.intellij.remote.TargetAwarePathMappingProvider;
import com.intellij.util.PathMappingSettings;
import com.intellij.util.PlatformUtils;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.execution.ParametersListUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.console.PyDebugConsoleBuilder;
import com.jetbrains.python.debugger.PyDebugRunner;
import com.jetbrains.python.debugger.PyDebuggerOptionsProvider;
import com.jetbrains.python.debugger.PyTargetPathMapper;
import com.jetbrains.python.facet.LibraryContributingFacet;
import com.jetbrains.python.facet.PythonPathContributingFacet;
import com.jetbrains.python.library.PythonLibraryType;
import com.jetbrains.python.packaging.PyExecutionException;
import com.jetbrains.python.remote.PyRemotePathMapper;
import com.jetbrains.python.remote.PyRemoteSdkAdditionalData;
import com.jetbrains.python.run.target.HelpersAwareTargetEnvironmentRequest;
import com.jetbrains.python.run.target.PySdkTargetPaths;
import com.jetbrains.python.run.target.PythonCommandLineTargetEnvironmentProvider;
import com.jetbrains.python.sdk.*;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import com.jetbrains.python.sdk.flavors.conda.CondaPythonExecKt;
import com.jetbrains.python.target.PyTargetAwareAdditionalData;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;

import static com.intellij.execution.util.EnvFilesUtilKt.configureEnvsFromFiles;
import static com.jetbrains.python.run.PythonScriptCommandLineState.getExpandedWorkingDir;

/**
 * Since this state is async, any method could be called on any thread
 *
 * @author traff, Leonid Shalupov
 */
public abstract class PythonCommandLineState extends CommandLineState {
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

  /**
   * The port number to use for a server socket. '0' means the port will be automatically allocated.
   */
  private static final int SERVER_SOCKET_PORT = 0;

  /**
   * The length of the backlog to use for a server socket. '0' means the length of queue will be chosen by Java Platform.
   */
  private static final int SERVER_SOCKET_BACKLOG = 0;

  private final AbstractPythonRunConfiguration<?> myConfig;

  private Boolean myMultiprocessDebug = null;
  private boolean myRunWithPty = PtyCommandLine.isEnabled();

  public boolean isDebug() {
    return PyDebugRunner.PY_DEBUG_RUNNER.equals(getEnvironment().getRunner().getRunnerId());
  }

  public static ServerSocket createServerSocket() throws ExecutionException {
    final ServerSocket serverSocket;
    try {
      serverSocket = new ServerSocket(SERVER_SOCKET_PORT, SERVER_SOCKET_BACKLOG, InetAddress.getLoopbackAddress());
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

  public @Nullable PythonSdkFlavor getSdkFlavor() {
    return PythonSdkFlavor.getFlavor(myConfig.getInterpreterPath());
  }

  public @Nullable Sdk getSdk() {
    return myConfig.getSdk();
  }

  public AbstractPythonRunConfiguration<?> getConfig() {
    return myConfig;
  }

  @Override
  public @NotNull ExecutionResult execute(@NotNull Executor executor, @NotNull ProgramRunner<?> runner) throws ExecutionException {
    return execute(executor, new CommandLinePatcher[0]);
  }

  /**
   * <i>To be deprecated. The part of the legacy implementation based on
   * {@link GeneralCommandLine}.</i>
   */
  public ExecutionResult execute(Executor executor, CommandLinePatcher... patchers) throws ExecutionException {
    return execute(executor, getDefaultPythonProcessStarter(), patchers);
  }

  public @Nullable ExecutionResult execute(@NotNull Executor executor) throws ExecutionException {
    return execute(executor, (targetEnvironmentRequest, pythonScript) -> pythonScript);
  }

  public final @NotNull List<String> getConfiguredInterpreterParameters() {
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

  /**
   * Please do not overuse {@code null} return value. {@code null} value is utilized by {@link PythonScriptCommandLineState} when starting
   * Python run configurations with "Run with Python Console" flag. It is more a workaround, so please annotate the overridden methods with
   * {@link NotNull} where possible.
   */
  public @Nullable ExecutionResult execute(@NotNull Executor executor,
                                           @NotNull PythonScriptTargetedCommandLineBuilder converter) throws ExecutionException {
    final ProcessHandler processHandler = startProcess(converter);
    final ConsoleView console = createAndAttachConsoleInEDT(myConfig.getProject(), processHandler, executor);
    return new DefaultExecutionResult(console, processHandler, createActions(console, processHandler));
  }

  protected @NotNull ConsoleView createAndAttachConsole(Project project, ProcessHandler processHandler, Executor executor)
    throws ExecutionException {
    final ConsoleView consoleView = createConsoleBuilder(project).getConsole();
    consoleView.addMessageFilter(createUrlFilter(processHandler));
    consoleView.addMessageFilter(new PythonImportErrorFilter(project));

    addTracebackFilter(project, consoleView, processHandler);

    consoleView.attachToProcess(processHandler);
    return consoleView;
  }

  protected void addTracebackFilter(@NotNull Project project, @NotNull ConsoleView consoleView, @NotNull ProcessHandler processHandler) {
    // TODO workaround
    if (PythonSdkUtil.isRemote(myConfig.getSdk()) && processHandler instanceof ProcessControlWithMappings) {
      consoleView
        .addMessageFilter(new PyRemoteTracebackFilter(project, getExpandedWorkingDir(myConfig), (ProcessControlWithMappings)processHandler));
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
  protected @NotNull ProcessHandler startProcess() throws ExecutionException {
    return startProcess(getDefaultPythonProcessStarter());
  }

  /**
   * <i>To be deprecated. The part of the legacy implementation based on {@link GeneralCommandLine}.</i>
   * <p>
   * Patches the command line parameters applying patchers from first to last, and then runs it.
   *
   * @param patchers any number of patchers; any patcher may be null, and the whole argument may be null.
   * @return handler of the started process
   */
  protected @NotNull ProcessHandler startProcess(PythonProcessStarter processStarter, CommandLinePatcher... patchers) throws ExecutionException {
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
  protected @NotNull ProcessHandler startProcess(@NotNull PythonScriptTargetedCommandLineBuilder builder)
    throws ExecutionException {
    HelpersAwareTargetEnvironmentRequest helpersAwareTargetRequest = getPythonTargetInterpreter();

    Sdk sdk = getSdk();
    if (sdk != null) {
      RunConfigurationTargetEnvironmentAdjuster adjuster =
        RunConfigurationTargetEnvironmentAdjuster.Factory.findTargetEnvironmentRequestAdjuster(sdk);
      if (adjuster != null) {
        adjuster.adjust(helpersAwareTargetRequest.getTargetEnvironmentRequest(), myConfig);
      }
    }

    // The original Python script to be executed
    PythonExecution pythonScript = buildPythonExecutionFinal(helpersAwareTargetRequest, getSdk());

    // Python script that may be the debugger script that runs the original script
    PythonExecution realPythonExecution = builder.build(helpersAwareTargetRequest, pythonScript);

    if (myConfig instanceof PythonRunConfiguration pythonConfig) {
      String inputFilePath = pythonConfig.getInputFile();
      if (pythonConfig.isRedirectInput() && !StringUtil.isEmptyOrSpaces(inputFilePath)) {
        realPythonExecution.withInputFile(new File(inputFilePath));
      }
    }

    // TODO [Targets API] [major] Meaningful progress indicator should be taken
    EmptyProgressIndicator progressIndicator = new EmptyProgressIndicator();
    TargetEnvironment targetEnvironment =
      helpersAwareTargetRequest.getTargetEnvironmentRequest().prepareEnvironment(TargetProgressIndicator.EMPTY);

    // TODO Detect and discard existing overrides of configured parameters.
    List<String> allInterpreterParameters = Streams.concat(getConfiguredInterpreterParameters().stream(),
                                                        realPythonExecution.getAdditionalInterpreterParameters().stream()).toList();
    TargetedCommandLine targetedCommandLine =
      PythonScripts.buildTargetedCommandLine(realPythonExecution, targetEnvironment, sdk, allInterpreterParameters, myRunWithPty);

    // TODO [Targets API] `myConfig.isPassParentEnvs` must be handled (at least for the local case)
    ProcessHandler processHandler = doStartProcess(targetEnvironment, targetedCommandLine, progressIndicator);

    // Attach extensions
    PythonRunConfigurationExtensionsManager.Companion.getInstance()
      .attachExtensionsToProcess(myConfig, processHandler, getRunnerSettings());

    processHandler.addProcessListener(new ProcessListener() {
      @Override
      public void processTerminated(@NotNull ProcessEvent event) {
        targetEnvironment.shutdown();
      }
    });

    return processHandler;
  }

  private @NotNull PythonExecution buildPythonExecutionFinal(HelpersAwareTargetEnvironmentRequest helpersAwareTargetRequest, @Nullable Sdk sdk) {
    TargetEnvironmentRequest targetEnvironmentRequest = helpersAwareTargetRequest.getTargetEnvironmentRequest();
    PythonExecution pythonExecution = buildPythonExecution(helpersAwareTargetRequest);
    pythonExecution.setWorkingDir(getPythonExecutionWorkingDir(targetEnvironmentRequest));
    initEnvironment(myConfig.getProject(), pythonExecution, myConfig, createRemotePathMapper(), isDebug(), helpersAwareTargetRequest, sdk);
    customizePythonExecutionEnvironmentVars(helpersAwareTargetRequest, pythonExecution.getEnvs(), myConfig.isPassParentEnvs());
    PythonScripts.ensureProjectSdkAndModuleDirsAreOnTarget(targetEnvironmentRequest, myConfig.getProject(), myConfig.getModule());
    return pythonExecution;
  }

  /**
   * Returns the promise to the working directory path for this
   * {@link PythonCommandLineState}. The working directory is resolved within
   * the uploads that are registered in the provided request.
   *
   * @param targetEnvironmentRequest the environment to explore for the working directory upload
   * @return the promise to the working directory path
   */
  protected @Nullable Function<TargetEnvironment, String> getPythonExecutionWorkingDir(@NotNull TargetEnvironmentRequest targetEnvironmentRequest) {
    // the following working directory is located on the local machine
    String workingDir = myConfig.getWorkingDirectorySafe();
    if (!StringUtil.isEmptyOrSpaces(workingDir)) {
      return getTargetPath(targetEnvironmentRequest, Path.of(workingDir));
    }
    return null;
  }

  /**
   * <i>To be deprecated. The part of the legacy implementation based on
   * {@link GeneralCommandLine}.</i>
   */
  protected final @NotNull PythonProcessStarter getDefaultPythonProcessStarter() {
    return (config, commandLine) -> {
      Sdk sdk = PythonSdkUtil.findSdkByPath(myConfig.getInterpreterPath());
      assert sdk != null : "No SDK For " + myConfig.getInterpreterPath();
      final ProcessHandler processHandler;
      var additionalData = sdk.getSdkAdditionalData();
      if (additionalData instanceof PyRemoteSdkAdditionalDataMarker) {
        assert additionalData instanceof PyRemoteSdkAdditionalData : "additionalData is remote, but not legacy. Is it a target-based? " +
                                                                     additionalData;
        PyRemotePathMapper pathMapper = createRemotePathMapper();
        processHandler = PyRemoteProcessStarter.startLegacyRemoteProcess((PyRemoteSdkAdditionalData)additionalData, commandLine,
                                                                         myConfig.getProject(), pathMapper);
      }
      else {
        processHandler = doCreateProcess(commandLine);
        ProcessTerminatedListener.attach(processHandler);
      }
      return processHandler;
    };
  }

  private @NotNull ProcessHandler doStartProcess(@NotNull TargetEnvironment targetEnvironment,
                                                 @NotNull TargetedCommandLine commandLine,
                                                 @NotNull ProgressIndicator progressIndicator) throws ExecutionException {
    final ProcessHandler processHandler;
    Process process = targetEnvironment.createProcess(commandLine, progressIndicator);
    // TODO [Targets API] [major] The command line should be prefixed with the interpreter identifier (f.e. Docker container id)
    String commandLineString = StringUtil.join(commandLine.getCommandPresentation(targetEnvironment), " ");
    processHandler = createPtyAwaredProcessHandler(process, commandLineString, targetEnvironment, commandLine);
    ProcessTerminatedListener.attach(processHandler);
    return processHandler;
  }

  protected final @Nullable PyRemotePathMapper createRemotePathMapper() {
    if (myConfig.getMappingSettings() == null) {
      return null;
    }
    else {
      return PyRemotePathMapper.fromSettings(myConfig.getMappingSettings(), PyRemotePathMapper.PyPathMappingType.USER_DEFINED);
    }
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
  public final @NotNull GeneralCommandLine generateCommandLine(CommandLinePatcher @Nullable [] patchers) {
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
  private static @NotNull GeneralCommandLine applyPatchers(@NotNull GeneralCommandLine commandLine, CommandLinePatcher @Nullable [] patchers) {
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

  private @NotNull ProcessHandler createPtyAwaredProcessHandler(@NotNull Process process,
                                                                @NotNull String commandLineString,
                                                                @NotNull TargetEnvironment targetEnvironment,
                                                                @NotNull TargetedCommandLine commandLine) {
    ProcessHandler processHandler = createProcessHandler(process, commandLineString, targetEnvironment, commandLine);
    if (processHandler instanceof OSProcessHandler osProcessHandler) {
      osProcessHandler.setHasPty(myRunWithPty);
    }
    return processHandler;
  }

  protected @NotNull ProcessHandler createProcessHandler(@NotNull Process process,
                                                         @NotNull String commandLineString,
                                                         @NotNull TargetEnvironment targetEnvironment,
                                                         @NotNull TargetedCommandLine commandLine) {
    if (targetEnvironment instanceof LocalTargetEnvironment) {
      // TODO This special treatment of local target must be replaced with a generalized approach
      //  (f.e. with an ability of a target environment to match arbitrary local path to a target one)
      if (isDebug()) {
        return new PyDebugProcessHandler(process, commandLineString, commandLine.getCharset());
      }
      return new PythonProcessHandler(process, commandLineString, commandLine.getCharset());
    }
    PathMappingSettings pathMappingSettings = new PathMappingSettings();
    // add mappings from run configuration on top
    PathMappingSettings runConfigurationPathMappings = myConfig.myMappingSettings;
    if (runConfigurationPathMappings != null) {
      pathMappingSettings.addAll(runConfigurationPathMappings);
    }
    // add path mappings configured in SDK, they will be handled in second place
    PathMappingSettings sdkPathMappings = getSdkPathMappings();
    if (sdkPathMappings != null) {
      // filter out any deployment paths, as we want to resolve sources to their local counterparts when possible rather
      // than the files copied from the remote end
      var deploymentPaths = getDeploymentPaths();
      pathMappingSettings.addAll(
        sdkPathMappings.getPathMappings()
          .stream()
          .filter(mapping -> !deploymentPaths.contains(mapping.getRemoteRoot()))
          .toList()
      );
    }
    final boolean isMostlySilentProcess = false;
    PyTargetPathMapper consolidatedPathMappings = new PyTargetPathMapper(targetEnvironment, pathMappingSettings);
    return PyCustomProcessHandlerProvider.createProcessHandler(process, targetEnvironment, commandLineString, commandLine.getCharset(),
                                                               consolidatedPathMappings, isMostlySilentProcess, myRunWithPty);
  }

  /**
   * Collects deployment paths from suitable mapping providers.
   *
   * If the current SDK additional data is not a {@code PyTargetAwareAdditionalData}, then an empty set is returned.
   *
   * @return a set of paths on remote file systems
   */
  private @NotNull Set<String> getDeploymentPaths() {
    Sdk sdk = myConfig.getSdk();
    Set<String> deploymentPaths = new HashSet<String>();
    if (sdk != null) {
      SdkAdditionalData sdkAdditionalData = sdk.getSdkAdditionalData();
      if (sdkAdditionalData instanceof PyTargetAwareAdditionalData data) {
        var providers = TargetAwarePathMappingProvider.Companion.getSuitableMappingProviders(data);
        for (TargetAwarePathMappingProvider provider : providers) {
          var pathMappings = provider.getPathMappingSettings(myConfig.getProject(), data).getPathMappings();
          for (PathMappingSettings.PathMapping mapping : pathMappings) {
            deploymentPaths.add(mapping.getRemoteRoot());
          }
        }
      }
    }
    return deploymentPaths;
  }

  private @Nullable PathMappingSettings getSdkPathMappings() {
    Sdk sdk = myConfig.getSdk();
    if (sdk != null) {
      SdkAdditionalData sdkAdditionalData = sdk.getSdkAdditionalData();
      if (sdkAdditionalData instanceof RemoteSdkProperties) {
        return ((RemoteSdkProperties)sdkAdditionalData).getPathMappings();
      }
    }
    return null;
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
  public @NotNull GeneralCommandLine generateCommandLine() {
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
  protected @NotNull PythonExecution buildPythonExecution(@NotNull HelpersAwareTargetEnvironmentRequest helpersAwareRequest) {
    throw new UnsupportedOperationException("The implementation of Run Configuration based on Targets API is absent");
  }

  /**
   * <i>To be deprecated. The part of the legacy implementation based on
   * {@link GeneralCommandLine}.</i>
   */
  public static @NotNull GeneralCommandLine createPythonCommandLine(Project project,
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
    addCommonEnvironmentVariables(getInterpreterPath(project, runParams), env, true);

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
                                     @Nullable PyRemotePathMapper pathMapper,
                                     @Nullable Sdk sdk) {
    initEnvironment(project, commandLine, runParams, pathMapper, false, helpersAwareTargetRequest, sdk);
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
                                      @NotNull HelpersAwareTargetEnvironmentRequest helpersAwareTargetRequest,
                                      @Nullable Sdk sdk) {
    Map<String, String> env = Maps.newHashMap();
    var envParameters = configureEnvsFromFiles(runParams, true);
    env.putAll(envParameters);
    if (runParams.getEnvs() != null) {
      env.putAll(runParams.getEnvs());
    }
    boolean addPyCharmHosted = true;
    if (sdk != null && !CondaPythonExecKt.getUsePythonForLocalConda()) {
      addPyCharmHosted = PySdkExtKt.getOrCreateAdditionalData(sdk).getFlavor().providePyCharmHosted();
    }
    addCommonEnvironmentVariables(getInterpreterPath(project, runParams), env, addPyCharmHosted);

    setupVirtualEnvVariables(runParams, env, runParams.getSdkHome());

    // Carefully patch environment variables
    Map<String, Function<TargetEnvironment, String>> map =
      ContainerUtil.map2Map(env.entrySet(), e -> Pair.create(e.getKey(), TargetEnvironmentFunctions.constant(e.getValue())));
    TargetEnvironmentRequest targetEnvironmentRequest = helpersAwareTargetRequest.getTargetEnvironmentRequest();
    PythonScripts.extendEnvs(commandLine, map, targetEnvironmentRequest.getTargetPlatform());

    setupEncodingEnvs(commandLine, commandLine.getCharset());

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

  /**
   * @param addPyCharmHosted see {@link PythonSdkFlavor#providePyCharmHosted()}
   */
  protected static void addCommonEnvironmentVariables(@Nullable String homePath, Map<String, String> env, boolean addPyCharmHosted) {
    PythonEnvUtil.setPythonUnbuffered(env);
    if (homePath != null) {
      PythonEnvUtil.resetHomePathChanges(homePath, env);
    }
    if (addPyCharmHosted) {
      env.put("PYCHARM_HOSTED", "1");
    }
  }

  // TODO add @NotNull for envs
  public void customizeEnvironmentVars(Map<String, String> envs, boolean passParentEnvs) {
  }

  protected void customizePythonExecutionEnvironmentVars(@NotNull HelpersAwareTargetEnvironmentRequest helpersAwareTargetRequest,
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
    Sdk sdk = config.getSdk();
    if (sdk != null) {
      Module module = getModule(project, config);
      buildPythonPath(project, module, pythonScript, sdk, pathMapper, config.isPassParentEnvs(),
                      config.shouldAddContentRoots(), config.shouldAddSourceRoots(), isDebug, targetEnvironmentRequest);
    }
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
                                     @NotNull Sdk pythonSdk,
                                     @Nullable PyRemotePathMapper pathMapper,
                                     boolean passParentEnvs,
                                     boolean shouldAddContentRoots,
                                     boolean shouldAddSourceRoots,
                                     boolean isDebug,
                                     @NotNull TargetEnvironmentRequest targetEnvironmentRequest) {
    List<Function<TargetEnvironment, String>> pathList = new ArrayList<>();
    var data = pythonSdk.getSdkAdditionalData();
    if (data != null) {
      pathList.addAll(TargetedPythonPaths.getAddedPaths(data));
    }
    pathList.addAll(TargetedPythonPaths.collectPythonPath(project, module, pythonSdk, pathMapper,
                                                          shouldAddContentRoots, shouldAddSourceRoots, isDebug));
    initPythonPath(pythonScript, passParentEnvs, pathList, targetEnvironmentRequest);
  }

  /**
   * Doesn't support target
   *
   * @deprecated
   */
  @Deprecated
  public static void initPythonPath(@NotNull GeneralCommandLine commandLine,
                                    boolean passParentEnvs,
                                    @NotNull List<String> pathList,
                                    final String interpreterPath) {
    final PythonSdkFlavor<?> flavor = PythonSdkFlavor.getFlavor(interpreterPath);
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
                                    @NotNull TargetEnvironmentRequest targetEnvironmentRequest) {
    TargetedPythonPaths.initPythonPath(pythonScript.getEnvs(), passParentEnvs, pathList, targetEnvironmentRequest, true);
  }

  public static @NotNull List<String> getAddedPaths(@NotNull Sdk pythonSdk) {
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
    else if (file.isDirectory()) {
      addIfNeeded(file, pathList);
    }
  }

  private static void addIfNeeded(final @NotNull VirtualFile file, final @NotNull Collection<String> pathList) {
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

  public static @NotNull Collection<String> collectPythonPath(@Nullable Module module,
                                                              @Nullable String sdkHome,
                                                              boolean shouldAddContentRoots,
                                                              boolean shouldAddSourceRoots,
                                                              boolean isDebug) {

    return Sets.newLinkedHashSet(collectPythonPath(module, shouldAddContentRoots, shouldAddSourceRoots));
  }

  private static @Nullable Module getModule(Project project, PythonRunParams config) {
    String name = config.getModuleName();
    return StringUtil.isEmpty(name) ? null : ModuleManager.getInstance(project).findModuleByName(name);
  }

  public static @NotNull Collection<String> collectPythonPath(@Nullable Module module) {
    return collectPythonPath(module, true, true);
  }

  public static @NotNull Collection<String> collectPythonPath(@Nullable Module module, boolean addContentRoots,
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

  public static @Nullable String getInterpreterPath(Project project, PythonRunParams config) {
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

  private @NotNull HelpersAwareTargetEnvironmentRequest getPythonTargetInterpreter() throws ExecutionException {
    Sdk sdk = getSdk();
    if (sdk == null) {
      throw new PyExecutionException(PyBundle.message("runcfg.error.message.cannot.find.python.interpreter"));
    }
    return getPythonTargetInterpreter(myConfig.getProject(), sdk);
  }

  public static @NotNull HelpersAwareTargetEnvironmentRequest getPythonTargetInterpreter(@NotNull Project project, @NotNull Sdk sdk) {
      HelpersAwareTargetEnvironmentRequest helpersAwareTargetRequest =
        PythonInterpreterTargetEnvironmentFactory.findPythonTargetInterpreter(sdk, project);
      if (helpersAwareTargetRequest == null) {
        throw new IllegalStateException("Cannot find execution environment for SDK " + sdk);
      }
      return helpersAwareTargetRequest;
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

  protected @NotNull UrlFilter createUrlFilter(ProcessHandler handler) {
    return new UrlFilter();
  }

  protected @NotNull Function<TargetEnvironment, String> getTargetPath(@NotNull TargetEnvironmentRequest targetEnvironmentRequest,
                                                                       @NotNull Path scriptPath) {
    return PySdkTargetPaths.getTargetPathForPythonScriptExecution(myConfig.getProject(), myConfig.getSdk(), createRemotePathMapper(),
                                                                  scriptPath);
  }

  /**
   * Decides whether the configuration should run.
   * This check happens at the moment that the run configuration is set to run (e.g., when the
   * user presses the green arrow button to run the configuration).
   * @return Returns `true` if the configuration should be allowed to run, `false` otherwise.
   */
  @RequiresEdt
  @ApiStatus.Internal
  public boolean canRun() {
    return true;
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
