// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution.run;

import com.intellij.build.*;
import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.StartBuildEvent;
import com.intellij.build.events.impl.StartBuildEventImpl;
import com.intellij.execution.*;
import com.intellij.execution.configurations.CommandLineState;
import com.intellij.execution.process.KillableColoredProcessHandler;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.target.*;
import com.intellij.execution.target.eel.EelTargetEnvironmentRequest;
import com.intellij.execution.target.value.TargetEnvironmentFunctions;
import com.intellij.execution.testDiscovery.JvmToggleAutoTestAction;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.actionSystem.AnAction;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.buildtool.BuildToolConsoleProcessAdapter;
import org.jetbrains.idea.maven.buildtool.MavenBuildEventProcessor;
import org.jetbrains.idea.maven.execution.MavenRebuildAction;
import org.jetbrains.idea.maven.execution.MavenResumeAction;
import org.jetbrains.idea.maven.execution.MavenRunConfiguration;
import org.jetbrains.idea.maven.execution.RunnerBundle;
import org.jetbrains.idea.maven.execution.target.MavenCommandLineSetup;
import org.jetbrains.idea.maven.execution.target.MavenRuntimeTargetConfiguration;
import org.jetbrains.idea.maven.execution.target.MavenRuntimeTypeConstants;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenParsingContext;
import org.jetbrains.idea.maven.server.MavenDistributionsCache;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import static org.jetbrains.idea.maven.server.MavenDistributionKt.isMaven4;

public class MavenTargetShCommandLineState extends CommandLineState implements TargetEnvironmentAwareRunProfileState {

  private final MavenRunConfiguration myConfiguration;
  private final @NotNull ExecutionEnvironment myEnvironment;
  private @NotNull TargetEnvironmentRequest myTargetEnvironmentRequest;
  private @NotNull TargetedCommandLineBuilder myCommandLineBuilder;

  public MavenTargetShCommandLineState(@NotNull ExecutionEnvironment environment, @NotNull MavenRunConfiguration configuration) {
    super(environment);
    myEnvironment = environment;
    myConfiguration = configuration;
  }

  @Override
  public TargetEnvironmentRequest createCustomTargetEnvironmentRequest() {
    Project project = myConfiguration.getProject();
    EelDescriptor eelDescriptor = EelProviderUtil.getEelDescriptor(project);
    if (eelDescriptor instanceof LocalEelDescriptor) {
      return null;
    }
    EelApi eel = EelProviderUtil.toEelApiBlocking(eelDescriptor);
    EelTargetEnvironmentRequest.Configuration configuration = new EelTargetEnvironmentRequest.Configuration(eel);
    MavenRuntimeTargetResolver targetResolver = new MavenRuntimeTargetResolver(project, eel);
    MavenRuntimeTargetConfiguration runtimeTarget = targetResolver.resolve(myConfiguration);
    configuration.addLanguageRuntime(runtimeTarget);
    return new EelTargetEnvironmentRequest(configuration);
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
    buildView.attachToProcess(new MavenHandlerFilterSpyWrapper(processHandler, useMaven4(), false));

    AnAction[] actions = new AnAction[]{BuildTreeFilters.createFilteringActionsGroup(new WeakFilterableSupplier<>(buildView))};
    DefaultExecutionResult res = new DefaultExecutionResult(buildView, processHandler, actions);
    List<AnAction> restartActions = new ArrayList<>();
    restartActions.add(new JvmToggleAutoTestAction());

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
        .withRestartActions(new MavenRebuildAction(myEnvironment),
                            new MavenResumeAction(processHandler, runner, myEnvironment,
                                                  ctx))
                       : startBuildEvent.withRestartActions(new MavenRebuildAction(myEnvironment));
  }

  @Override
  public @NotNull ExecutionResult execute(@NotNull Executor executor, @NotNull ProgramRunner<?> runner) throws ExecutionException {
    final ProcessHandler processHandler = startProcess();
    ExecutionEnvironment environment = myEnvironment;
    TargetEnvironment targetEnvironment = environment.getPreparedTargetEnvironment(this, TargetProgressIndicator.EMPTY);
    Function<String, String> targetFileMapper = path -> {
      return path != null && SystemInfo.isWindows && path.charAt(0) == '/' ? path.substring(1) : path;
    };
    LanguageRuntimeType.VolumeType mavenProjectFolderVolumeType = MavenRuntimeTypeConstants.getPROJECT_FOLDER_VOLUME().getType();
    Set<TargetEnvironment.UploadRoot> uploadVolumes = myTargetEnvironmentRequest.getUploadVolumes();
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

    TargetedCommandLineBuilder targetedCommandLineBuilder = myCommandLineBuilder;
    String targetWorkingDirectory = targetedCommandLineBuilder.build().getWorkingDirectory();
    String workingDir =
      targetWorkingDirectory != null ? targetFileMapper.apply(targetWorkingDirectory) : myEnvironment.getProject().getBasePath();
    ExternalSystemTaskId taskId =
      ExternalSystemTaskId.create(MavenUtil.SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, myConfiguration.getProject());
    DefaultBuildDescriptor descriptor =
      new DefaultBuildDescriptor(taskId, myConfiguration.getName(), workingDir, System.currentTimeMillis());
    return doRunExecute(executor, runner, taskId, descriptor, processHandler, targetFileMapper);
  }

  @Override
  public void prepareTargetEnvironmentRequest(
    @NotNull TargetEnvironmentRequest request,
    @NotNull TargetProgressIndicator targetProgressIndicator) throws ExecutionException {
    targetProgressIndicator.addSystemLine(ExecutionBundle.message("progress.text.prepare.target.requirements"));

    myTargetEnvironmentRequest = request;
    myCommandLineBuilder = createTargetedCommandLine(myTargetEnvironmentRequest);
  }

  private @Nullable BuildView createBuildView(@NotNull Executor executor,
                                              @NotNull BuildDescriptor descriptor,
                                              @NotNull ProcessHandler processHandler) throws ExecutionException {
    ConsoleView console = super.createConsole(executor);
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


  protected @NotNull TargetedCommandLineBuilder createTargetedCommandLine(@NotNull TargetEnvironmentRequest request)
    throws ExecutionException {
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

  @Override
  public void handleCreatedTargetEnvironment(@NotNull TargetEnvironment environment,
                                             @NotNull TargetProgressIndicator targetProgressIndicator) {
    TargetedCommandLineBuilder targetedCommandLineBuilder = myCommandLineBuilder;
    Objects.requireNonNull(targetedCommandLineBuilder.getUserData(MavenCommandLineSetup.getSetupKey()))
      .provideEnvironment(environment, targetProgressIndicator);
  }

  @Override
  protected @NotNull OSProcessHandler startProcess() throws ExecutionException {
    ExecutionEnvironment environment = myEnvironment;
    TargetEnvironment remoteEnvironment = environment.getPreparedTargetEnvironment(this, TargetProgressIndicator.EMPTY);
    TargetedCommandLine targetedCommandLine = myCommandLineBuilder.build();
    Process process = remoteEnvironment.createProcess(targetedCommandLine, new EmptyProgressIndicator());
    OSProcessHandler handler = createProcessHandler(remoteEnvironment, myCommandLineBuilder, targetedCommandLine, process);
    ProcessTerminatedListener.attach(handler);
    JavaRunConfigurationExtensionManager.getInstance()
      .attachExtensionsToProcess(myConfiguration, handler, getRunnerSettings());
    return handler;
  }

  protected @NotNull OSProcessHandler createProcessHandler(TargetEnvironment remoteEnvironment,
                                                           TargetedCommandLineBuilder targetedCommandLineBuilder,
                                                           TargetedCommandLine targetedCommandLine,
                                                           Process process) throws ExecutionException {
    return new KillableColoredProcessHandler.Silent(process,
                                                    targetedCommandLine.getCommandPresentation(remoteEnvironment),
                                                    targetedCommandLine.getCharset(),
                                                    targetedCommandLineBuilder.getFilesToDeleteOnTermination());
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
