// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution.run;

import com.intellij.build.*;
import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.StartBuildEvent;
import com.intellij.build.events.impl.StartBuildEventImpl;
import com.intellij.execution.*;
import com.intellij.execution.configurations.*;
import com.intellij.execution.process.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.target.*;
import com.intellij.execution.target.eel.EelTargetEnvironmentRequest;
import com.intellij.execution.target.local.LocalTargetEnvironment;
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest;
import com.intellij.execution.target.value.TargetEnvironmentFunctions;
import com.intellij.execution.testDiscovery.JvmToggleAutoTestAction;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfigurationViewManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.platform.eel.EelApi;
import com.intellij.platform.eel.EelDescriptor;
import com.intellij.platform.eel.provider.EelProviderUtil;
import com.intellij.platform.eel.provider.LocalEelDescriptor;
import com.intellij.terminal.TerminalExecutionConsole;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.buildtool.BuildToolConsoleProcessAdapter;
import org.jetbrains.idea.maven.buildtool.MavenBuildEventProcessor;
import org.jetbrains.idea.maven.execution.*;
import org.jetbrains.idea.maven.execution.target.MavenCommandLineSetup;
import org.jetbrains.idea.maven.execution.target.MavenRuntimeTargetConfiguration;
import org.jetbrains.idea.maven.execution.target.MavenRuntimeTypeConstants;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenParsingContext;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.server.MavenDistribution;
import org.jetbrains.idea.maven.server.MavenDistributionsCache;
import org.jetbrains.idea.maven.server.MavenWrapperDownloader;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import static org.jetbrains.idea.maven.server.MavenDistributionKt.isMaven4;

public class MavenCommandLineState extends JavaCommandLineState implements RemoteConnectionCreator {

  private final MavenRunConfiguration myConfiguration;
  private RemoteConnectionCreator myRemoteConnectionCreator;

  public MavenCommandLineState(@NotNull ExecutionEnvironment environment, @NotNull MavenRunConfiguration configuration) {
    super(environment);
    myConfiguration = configuration;
  }

  @Override
  public TargetEnvironmentRequest createCustomTargetEnvironmentRequest() {
    Project project = myConfiguration.getProject();
    EelDescriptor eelDescriptor = EelProviderUtil.getEelDescriptor(project);
    if (eelDescriptor instanceof LocalEelDescriptor) {
      return null;
    }
    EelApi eel = EelProviderUtil.upgradeBlocking(eelDescriptor);
    EelTargetEnvironmentRequest.Configuration configuration = new EelTargetEnvironmentRequest.Configuration(eel);
    MavenRuntimeTargetResolver targetResolver = new MavenRuntimeTargetResolver(project, eel);
    MavenRuntimeTargetConfiguration runtimeTarget = targetResolver.resolve(myConfiguration);
    configuration.addLanguageRuntime(runtimeTarget);
    return new EelTargetEnvironmentRequest(configuration);
  }

  @Override
  protected JavaParameters createJavaParameters() throws ExecutionException {
    if (getEnvironment().getTargetEnvironmentRequest() instanceof LocalTargetEnvironmentRequest) {
      JavaParameters parameters = myConfiguration.createJavaParameters(getEnvironment().getProject());
      JavaRunConfigurationExtensionManager.getInstance().updateJavaParameters(
        myConfiguration,
        parameters,
        getEnvironment().getRunnerSettings(),
        getEnvironment().getExecutor()
      );
      return parameters;
    }
    else {
      return new JavaParameters();
    }
  }

  @Override
  protected @Nullable ConsoleView createConsole(@NotNull Executor executor) throws ExecutionException {
    ConsoleView console = super.createConsole(executor);
    if (console != null && getEnvironment().getTargetEnvironmentRequest() instanceof LocalTargetEnvironmentRequest) {
      return JavaRunConfigurationExtensionManager.getInstance().decorateExecutionConsole(
        myConfiguration,
        getRunnerSettings(),
        console,
        executor
      );
    }
    else {
      return console;
    }
  }

  protected @Nullable ConsoleView createConsole(@NotNull Executor executor,
                                                @NotNull ProcessHandler processHandler,
                                                @NotNull Project project) throws ExecutionException {
    ConsoleView console = createConsoleView(executor, processHandler, project);
    if (console != null && getEnvironment().getTargetEnvironmentRequest() instanceof LocalTargetEnvironmentRequest) {
      return JavaRunConfigurationExtensionManager.getInstance()
        .decorateExecutionConsole(myConfiguration,
                                  getRunnerSettings(),
                                  console,
                                  executor);
    }
    else {
      return console;
    }
  }

  protected @Nullable ConsoleView createConsoleView(@NotNull Executor executor,
                                                    @NotNull ProcessHandler processHandler,
                                                    @NotNull Project project) throws ExecutionException {
    return emulateTerminal()
           ? new TerminalExecutionConsole(project, null)
           : super.createConsole(executor);
  }

  protected boolean emulateTerminal() {
    return !SystemInfo.isWindows &&
           myConfiguration.getGeneralSettings() != null &&
           myConfiguration.getGeneralSettings().isEmulateTerminal() &&
           getTargetEnvironmentRequest() instanceof LocalTargetEnvironmentRequest;
  }

  public ExecutionResult doDelegateBuildExecute(@NotNull Executor executor,
                                                @NotNull ProgramRunner runner,
                                                ExternalSystemTaskId taskId,
                                                DefaultBuildDescriptor descriptor,
                                                ProcessHandler processHandler,
                                                Function<String, String> targetFileMapper) throws ExecutionException {
    ConsoleView consoleView = createConsole(executor, processHandler, myConfiguration.getProject());
    BuildViewManager viewManager = getEnvironment().getProject().getService(BuildViewManager.class);
    descriptor.withProcessHandler(new MavenBuildHandlerFilterSpyWrapper(processHandler, useMaven4()), null);
    descriptor.withExecutionEnvironment(getEnvironment());
    StartBuildEventImpl startBuildEvent = new StartBuildEventImpl(descriptor, "");
    boolean withResumeAction = MavenResumeAction.isApplicable(getEnvironment().getProject(), getJavaParameters(), myConfiguration);
    MavenBuildEventProcessor eventProcessor =
      new MavenBuildEventProcessor(myConfiguration, viewManager, descriptor, taskId,
                                   targetFileMapper, getStartBuildEventSupplier(runner, processHandler, startBuildEvent, withResumeAction),
                                   useMaven4()
      );

    processHandler.addProcessListener(new BuildToolConsoleProcessAdapter(eventProcessor));
    DefaultExecutionResult res = new DefaultExecutionResult(consoleView, processHandler, new DefaultActionGroup());
    res.setRestartActions(new JvmToggleAutoTestAction());
    return res;
  }

  public ExecutionResult doRunExecute(@NotNull Executor executor,
                                      @NotNull ProgramRunner runner,
                                      ExternalSystemTaskId taskId,
                                      DefaultBuildDescriptor descriptor,
                                      ProcessHandler processHandler,
                                      @NotNull Function<String, String> targetFileMapper) throws ExecutionException {
    final BuildView buildView = createBuildView(executor, descriptor, processHandler);

    if (buildView == null) {
      MavenLog.LOG.warn("buildView is null for " + myConfiguration.getName());
    }
    MavenBuildEventProcessor eventProcessor =
      new MavenBuildEventProcessor(myConfiguration, buildView, descriptor, taskId, targetFileMapper, ctx ->
        new StartBuildEventImpl(descriptor, ""), useMaven4());

    processHandler.addProcessListener(new BuildToolConsoleProcessAdapter(eventProcessor));
    if (emulateTerminal()) {
      buildView.attachToProcess(processHandler);
    }
    else {
      buildView.attachToProcess(new MavenHandlerFilterSpyWrapper(processHandler, useMaven4()));
    }

    AnAction[] actions = new AnAction[]{BuildTreeFilters.createFilteringActionsGroup(buildView)};
    DefaultExecutionResult res = new DefaultExecutionResult(buildView, processHandler, actions);
    List<AnAction> restartActions = new ArrayList<>();
    restartActions.add(new JvmToggleAutoTestAction());

    if (MavenResumeAction.isApplicable(getEnvironment().getProject(), getJavaParameters(), myConfiguration)) {
      MavenResumeAction resumeAction =
        new MavenResumeAction(res.getProcessHandler(), runner, getEnvironment(), eventProcessor.getParsingContext());
      restartActions.add(resumeAction);
    }
    res.setRestartActions(restartActions.toArray(AnAction.EMPTY_ARRAY));
    return res;
  }

  private boolean useMaven4() {
    var mavenCache = MavenDistributionsCache.getInstance(myConfiguration.getProject());
    var mavenDistribution = mavenCache.getMavenDistribution(myConfiguration.getRunnerParameters().getWorkingDirPath());
    return isMaven4(mavenDistribution);
  }

  private @NotNull Function<MavenParsingContext, StartBuildEvent> getStartBuildEventSupplier(@NotNull ProgramRunner runner,
                                                                                             ProcessHandler processHandler,
                                                                                             StartBuildEventImpl startBuildEvent,
                                                                                             boolean withResumeAction) {
    return ctx ->
      withResumeAction ? startBuildEvent
        .withRestartActions(new MavenRebuildAction(getEnvironment()),
                            new MavenResumeAction(processHandler, runner, getEnvironment(),
                                                  ctx))
                       : startBuildEvent.withRestartActions(new MavenRebuildAction(getEnvironment()));
  }

  @Override
  public @NotNull ExecutionResult execute(@NotNull Executor executor, @NotNull ProgramRunner<?> runner) throws ExecutionException {
    checkMavenWrapperAndPatchJavaParams();
    final ProcessHandler processHandler = startProcess();
    ExecutionEnvironment environment = getEnvironment();
    TargetEnvironment targetEnvironment = environment.getPreparedTargetEnvironment(this, TargetProgressIndicator.EMPTY);
    Function<String, String> targetFileMapper = path -> {
      return path != null && SystemInfo.isWindows && path.charAt(0) == '/' ? path.substring(1) : path;
    };
    if (!(targetEnvironment instanceof LocalTargetEnvironment)) {
      TargetEnvironmentRequest targetEnvironmentRequest = getTargetEnvironmentRequest();
      LanguageRuntimeType.VolumeType mavenProjectFolderVolumeType = MavenRuntimeTypeConstants.getPROJECT_FOLDER_VOLUME().getType();
      Set<TargetEnvironment.UploadRoot> uploadVolumes = targetEnvironmentRequest.getUploadVolumes();
      for (TargetEnvironment.UploadRoot uploadVolume : uploadVolumes) {
        String localPath = uploadVolume.getLocalRootPath().toString();
        TargetEnvironment.TargetPath targetRootPath = uploadVolume.getTargetRootPath();
        if (targetRootPath instanceof TargetEnvironment.TargetPath.Temporary &&
            mavenProjectFolderVolumeType.getId().equals(((TargetEnvironment.TargetPath.Temporary)targetRootPath).getHint())) {
          String targetPath = TargetEnvironmentFunctions.getTargetUploadPath(uploadVolume).apply(targetEnvironment);
          targetFileMapper = createTargetFileMapper(targetEnvironment, localPath, targetPath);
          break;
        }
      }
    }

    TargetedCommandLineBuilder targetedCommandLineBuilder = getTargetedCommandLine();
    String targetWorkingDirectory = targetedCommandLineBuilder.build().getWorkingDirectory();
    String workingDir =
      targetWorkingDirectory != null ? targetFileMapper.apply(targetWorkingDirectory) : getEnvironment().getProject().getBasePath();
    ExternalSystemTaskId taskId =
      ExternalSystemTaskId.create(MavenUtil.SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, myConfiguration.getProject());
    DefaultBuildDescriptor descriptor =
      new DefaultBuildDescriptor(taskId, myConfiguration.getName(), workingDir, System.currentTimeMillis());
    if (MavenRunConfigurationType.isDelegate(getEnvironment())) {
      return doDelegateBuildExecute(executor, runner, taskId, descriptor, processHandler, targetFileMapper);
    }
    else {
      return doRunExecute(executor, runner, taskId, descriptor, processHandler, targetFileMapper);
    }
  }

  private @Nullable BuildView createBuildView(@NotNull Executor executor,
                                              @NotNull BuildDescriptor descriptor,
                                              @NotNull ProcessHandler processHandler) throws ExecutionException {
    ConsoleView console = createConsole(executor, processHandler, myConfiguration.getProject());
    if (console == null) {
      return null;
    }
    Project project = myConfiguration.getProject();
    ExternalSystemRunConfigurationViewManager viewManager = project.getService(ExternalSystemRunConfigurationViewManager.class);
    return new BuildView(project, console, descriptor, "build.toolwindow.run.selection.state", viewManager) {
      @Override
      public void onEvent(@NotNull Object buildId, @NotNull BuildEvent event) {
        super.onEvent(buildId, event);
        viewManager.onEvent(buildId, event);
      }
    };
  }

  private void checkMavenWrapperAndPatchJavaParams() {
    if (myConfiguration.getGeneralSettings() == null || !MavenUtil.isWrapper(myConfiguration.getGeneralSettings())) return;

    MavenDistributionsCache instance = MavenDistributionsCache.getInstance(myConfiguration.getProject());
    String workingDirPath = myConfiguration.getRunnerParameters().getWorkingDirPath();
    MavenDistribution wrapper = instance.getWrapper(workingDirPath);
    if (wrapper == null) {
      MavenWrapperDownloader.checkOrInstall(myConfiguration.getProject(), workingDirPath);
    }
    wrapper = instance.getWrapper(workingDirPath);
    if (wrapper == null) return;
    try {
      JavaParameters javaParameters = getJavaParameters();
      if (javaParameters == null || !javaParameters.getVMParametersList().hasProperty(MavenConstants.HOME_PROPERTY)) return;
      String mavenHomePath = wrapper.getMavenHome().toFile().getCanonicalPath();

      ParametersList vmParametersList = javaParameters.getVMParametersList();
      if (Objects.equals(vmParametersList.getPropertyValue(MavenConstants.HOME_PROPERTY), wrapper.getMavenHome().toString())) return;
      vmParametersList.addProperty(MavenConstants.HOME_PROPERTY, mavenHomePath);
    }
    catch (IOException | ExecutionException e) {
      MavenLog.LOG.error(e);
    }
  }

  @Override
  protected @NotNull TargetedCommandLineBuilder createTargetedCommandLine(@NotNull TargetEnvironmentRequest request)
    throws ExecutionException {
    if (request instanceof LocalTargetEnvironmentRequest) {
      TargetedCommandLineBuilder commandLineBuilder = super.createTargetedCommandLine(request);
      if (emulateTerminal()) {
        commandLineBuilder.setPtyOptions(getLocalTargetPtyOptions());
      }
      return commandLineBuilder;
    }
    if (request.getConfiguration() == null) {
      throw new CantRunException(RunnerBundle.message("cannot.find.target.environment.configuration"));
    }
    var settings = new MavenRunConfiguration.MavenSettings(myConfiguration.getProject());
    settings.setRunnerParameters(myConfiguration.getRunnerParameters());
    settings.setGeneralSettings(myConfiguration.getGeneralSettings());
    settings.setRunnerSettings(myConfiguration.getRunnerSettings());
    return new MavenCommandLineSetup(myConfiguration.getProject(), myConfiguration.getName(), request)
      .setupCommandLine(settings)
      .getCommandLine();
  }

  private static @NotNull PtyOptions getLocalTargetPtyOptions() {
    return new PtyOptions() {
      @Override
      public int getInitialColumns() {
        return LocalPtyOptions.defaults().getInitialColumns();
      }

      @Override
      public int getInitialRows() {
        return LocalPtyOptions.defaults().getInitialRows();
      }
    };
  }

  @Override
  public void handleCreatedTargetEnvironment(@NotNull TargetEnvironment environment,
                                             @NotNull TargetProgressIndicator targetProgressIndicator) {
    if (environment instanceof LocalTargetEnvironment) {
      super.handleCreatedTargetEnvironment(environment, targetProgressIndicator);
    }
    else {
      TargetedCommandLineBuilder targetedCommandLineBuilder = getTargetedCommandLine();
      Objects.requireNonNull(targetedCommandLineBuilder.getUserData(MavenCommandLineSetup.getSetupKey()))
        .provideEnvironment(environment, targetProgressIndicator);
    }
  }

  @Override
  protected @NotNull OSProcessHandler startProcess() throws ExecutionException {
    ExecutionEnvironment environment = getEnvironment();
    TargetEnvironment remoteEnvironment = environment.getPreparedTargetEnvironment(this, TargetProgressIndicator.EMPTY);
    TargetedCommandLineBuilder targetedCommandLineBuilder = getTargetedCommandLine();
    TargetedCommandLine targetedCommandLine = targetedCommandLineBuilder.build();
    Process process = remoteEnvironment.createProcess(targetedCommandLine, new EmptyProgressIndicator());
    OSProcessHandler handler = createProcessHandler(remoteEnvironment, targetedCommandLineBuilder, targetedCommandLine, process);
    ProcessTerminatedListener.attach(handler);
    JavaRunConfigurationExtensionManager.getInstance()
      .attachExtensionsToProcess(myConfiguration, handler, getRunnerSettings());
    return handler;
  }

  protected @NotNull OSProcessHandler createProcessHandler(TargetEnvironment remoteEnvironment,
                                                           TargetedCommandLineBuilder targetedCommandLineBuilder,
                                                           TargetedCommandLine targetedCommandLine,
                                                           Process process) throws ExecutionException {
    if (emulateTerminal()) {
      return new MavenKillableProcessHandler(process,
                                                                   targetedCommandLine.getCommandPresentation(remoteEnvironment),
                                                                   targetedCommandLine.getCharset(),
                                                                   targetedCommandLineBuilder.getFilesToDeleteOnTermination(),
                                             useMaven4());
    }
    else {
      return new KillableColoredProcessHandler.Silent(process,
                                                      targetedCommandLine.getCommandPresentation(remoteEnvironment),
                                                      targetedCommandLine.getCharset(),
                                                      targetedCommandLineBuilder.getFilesToDeleteOnTermination());
    }
  }

  public RemoteConnectionCreator getRemoteConnectionCreator() {
    if (myRemoteConnectionCreator == null) {
      try {
        myRemoteConnectionCreator = myConfiguration.createRemoteConnectionCreator(getJavaParameters());
      }
      catch (ExecutionException e) {
        throw new RuntimeException("Cannot create java parameters", e);
      }
    }
    return myRemoteConnectionCreator;
  }

  @Override
  public @Nullable RemoteConnection createRemoteConnection(ExecutionEnvironment environment) {
    return getRemoteConnectionCreator().createRemoteConnection(environment);
  }

  @Override
  public boolean isPollConnection() {
    return getRemoteConnectionCreator().isPollConnection();
  }

  private static @NotNull Function<String, String> createTargetFileMapper(@NotNull TargetEnvironment targetEnvironment,
                                                                          @NotNull String projectRootlocalPath,
                                                                          @NotNull String projectRootTargetPath) {
    return path -> {
      if (path == null) return null;
      boolean isWindows = targetEnvironment.getTargetPlatform().getPlatform() == Platform.WINDOWS;
      path = isWindows && path.charAt(0) == '/' ? path.substring(1) : path;
      if (path.startsWith(projectRootTargetPath)) {
        return Paths.get(projectRootlocalPath, StringUtil.trimStart(path, projectRootTargetPath)).toString();
      }
      // workaround for "var -> private/var" symlink
      // TODO target absolute path can be used instead for such mapping of target file absolute paths
      if (path.startsWith("/private" + projectRootTargetPath)) {
        return Paths.get(projectRootlocalPath, StringUtil.trimStart(path, "/private" + projectRootTargetPath)).toString();
      }
      return path;
    };
  }
}
