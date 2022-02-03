// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution;

import com.intellij.CommonBundle;
import com.intellij.build.*;
import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.StartBuildEvent;
import com.intellij.build.events.impl.StartBuildEventImpl;
import com.intellij.build.process.BuildProcessHandler;
import com.intellij.debugger.impl.RemoteConnectionBuilder;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.diagnostic.logging.LogConfigurationPanel;
import com.intellij.execution.*;
import com.intellij.execution.configurations.*;
import com.intellij.execution.impl.SingleConfigurationConfigurable;
import com.intellij.execution.process.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.target.*;
import com.intellij.execution.target.local.LocalTargetEnvironment;
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest;
import com.intellij.execution.target.value.TargetEnvironmentFunctions;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.util.JavaParametersUtil;
import com.intellij.execution.wsl.target.WslTargetEnvironmentConfiguration;
import com.intellij.execution.wsl.target.WslTargetEnvironmentRequest;
import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfigurationViewManager;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.options.SettingsEditorGroup;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.annotations.Transient;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.buildtool.BuildToolConsoleProcessAdapter;
import org.jetbrains.idea.maven.buildtool.MavenBuildEventProcessor;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.MavenPropertyResolver;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.execution.run.configuration.MavenRunConfigurationSettingsEditor;
import org.jetbrains.idea.maven.execution.target.MavenCommandLineSetup;
import org.jetbrains.idea.maven.execution.target.MavenRuntimeTargetConfiguration;
import org.jetbrains.idea.maven.execution.target.MavenRuntimeType;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenParsingContext;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.project.MavenGeneralSettingsEditor;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.server.MavenDistribution;
import org.jetbrains.idea.maven.server.MavenDistributionsCache;
import org.jetbrains.idea.maven.server.MavenWrapperDownloader;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

public class MavenRunConfiguration extends LocatableConfigurationBase implements ModuleRunProfile, TargetEnvironmentAwareRunProfile {
  private @NotNull MavenSettings settings = new MavenSettings(getProject());

  protected MavenRunConfiguration(Project project, ConfigurationFactory factory, String name) {
    super(project, factory, name);
  }

  public @Nullable MavenGeneralSettings getGeneralSettings() {
    return settings.getGeneralSettings();
  }

  public void setGeneralSettings(@Nullable MavenGeneralSettings settings) {
    this.settings.setGeneralSettings(settings);
  }

  public @Nullable MavenRunnerSettings getRunnerSettings() {
    return settings.getRunnerSettings();
  }

  public void setRunnerSettings(@Nullable MavenRunnerSettings settings) {
    this.settings.setRunnerSettings(settings);
  }

  public @NotNull MavenRunnerParameters getRunnerParameters() {
    return settings.getRunnerParameters();
  }

  public void setRunnerParameters(@NotNull MavenRunnerParameters parameters) {
    settings.setRunnerParameters(parameters);
  }

  @Override
  public MavenRunConfiguration clone() {
    MavenRunConfiguration clone = (MavenRunConfiguration)super.clone();
    clone.settings = settings.clone();
    clone.initializeSettings();
    return clone;
  }

  private void initializeSettings() {
    if (StringUtil.isEmptyOrSpaces(settings.getRunnerParameters().getWorkingDirPath())) {
      String rootProjectPath = getRootProjectPath();
      if (rootProjectPath != null) {
        settings.getRunnerParameters().setWorkingDirPath(rootProjectPath);
      }
    }
  }

  private @Nullable String getRootProjectPath() {
    MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(getProject());
    MavenProject rootProject = ContainerUtil.getFirstItem(projectsManager.getRootProjects());
    return ObjectUtils.doIfNotNull(rootProject, it -> it.getDirectory());
  }

  @ApiStatus.Internal
  public JavaRunConfigurationExtensionManager getExtensionsManager() {
    return JavaRunConfigurationExtensionManager.getInstance();
  }

  @NotNull
  @Override
  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    if (Registry.is("ide.new.run.config", true)) {
      return new MavenRunConfigurationSettingsEditor(this);
    }

    SettingsEditorGroup<MavenRunConfiguration> group = new SettingsEditorGroup<>();

    MavenRunnerParametersSettingEditor runnerParamsEditor = new MavenRunnerParametersSettingEditor(getProject());
    MavenGeneralSettingsEditor generalSettingsEditor = new MavenGeneralSettingsEditor(getProject());
    MavenRunnerSettingsEditor runnerSettingsEditor = new MavenRunnerSettingsEditor(getProject());

    runnerParamsEditor.registerSettingsWatcher(new MavenRCSettingsWatcherImpl(getProject(), "maven.params", false));
    generalSettingsEditor.registerSettingsWatcher(new MavenRCSettingsWatcherImpl(getProject(), "maven.general", true));
    runnerSettingsEditor.registerSettingsWatcher(new MavenRCSettingsWatcherImpl(getProject(), "maven.runner", true));

    group.addEditor(RunnerBundle.message("maven.runner.parameters.title"), runnerParamsEditor);
    group.addEditor(CommonBundle.message("tab.title.general"), generalSettingsEditor);
    group.addEditor(RunnerBundle.message("maven.tab.runner"), runnerSettingsEditor);
    group.addEditor(ExecutionBundle.message("logs.tab.title"), new LogConfigurationPanel<>());

    return group;
  }

  @ApiStatus.Internal
  @Nullable
  public static String getTargetName(SettingsEditor<MavenRunConfiguration> mavenRunConfigurationSettingsEditor) {
    return DataManager.getInstance().getDataContext(mavenRunConfigurationSettingsEditor.getComponent())
      .getData(SingleConfigurationConfigurable.RUN_ON_TARGET_NAME_KEY);
  }

  public JavaParameters createJavaParameters(@Nullable Project project) throws ExecutionException {
    return MavenExternalParameters.createJavaParameters(project, getRunnerParameters(), getGeneralSettings(), getRunnerSettings(), this);
  }

  @Override
  public RunProfileState getState(@NotNull final Executor executor, @NotNull final ExecutionEnvironment env) {
    return new MavenCommandLineState(env, this);
  }

  @NotNull
  public RemoteConnectionCreator createRemoteConnectionCreator(JavaParameters javaParameters) {
    return new ExecRemoteConnectionCreator(javaParameters, this);
  }

  @Override
  public void readExternal(@NotNull Element element) throws InvalidDataException {
    super.readExternal(element);
    settings.readExternal(element);
    getExtensionsManager().readExternal(this, element);
  }

  @Override
  public void writeExternal(@NotNull Element element) throws WriteExternalException {
    super.writeExternal(element);
    settings.writeExternal(element);
    getExtensionsManager().writeExternal(this, element);
  }

  @Override
  public String suggestedName() {
    return MavenRunConfigurationType.generateName(getProject(), getRunnerParameters());
  }

  @Override
  public boolean canRunOn(@NotNull TargetEnvironmentConfiguration target) {
    return target.getRuntimes().findByType(MavenRuntimeTargetConfiguration.class) != null;
  }

  @Override
  public @Nullable LanguageRuntimeType<?> getDefaultLanguageRuntimeType() {
    return LanguageRuntimeType.EXTENSION_NAME.findExtension(MavenRuntimeType.class);
  }

  @Override
  public @Nullable String getDefaultTargetName() {
    return getOptions().getRemoteTarget();
  }

  @Override
  public void setDefaultTargetName(@Nullable String targetName) {
    getOptions().setRemoteTarget(targetName);
  }

  @Override
  public void onNewConfigurationCreated() {
    super.onNewConfigurationCreated();
    if (!getName().equals(suggestedName())) {
      // prevent RC name reset by RunConfigurable.installUpdateListeners on target change in UI
      getOptions().setNameGenerated(false);
    }
  }

  // TODO: make private
  @ApiStatus.Internal
  public static class MavenSettings implements Cloneable {
    public static final String TAG = "MavenSettings";

    public @Nullable MavenGeneralSettings myGeneralSettings;
    public @Nullable MavenRunnerSettings myRunnerSettings;
    public @Nullable MavenRunnerParameters myRunnerParameters;

    /* reflection only */
    public MavenSettings() {
    }

    public MavenSettings(Project project) {
      myRunnerParameters = new MavenRunnerParameters();
    }

    @Transient
    public @Nullable MavenGeneralSettings getGeneralSettings() {
      return myGeneralSettings;
    }

    public void setGeneralSettings(@Nullable MavenGeneralSettings generalSettings) {
      myGeneralSettings = generalSettings;
    }

    @Transient
    public @Nullable MavenRunnerSettings getRunnerSettings() {
      return myRunnerSettings;
    }

    public void setRunnerSettings(@Nullable MavenRunnerSettings runnerSettings) {
      myRunnerSettings = runnerSettings;
    }

    @Transient
    public @NotNull MavenRunnerParameters getRunnerParameters() {
      return Objects.requireNonNull(myRunnerParameters);
    }

    public void setRunnerParameters(@NotNull MavenRunnerParameters runnerParameters) {
      myRunnerParameters = runnerParameters;
    }

    @Override
    protected MavenSettings clone() {
      try {
        MavenSettings clone = (MavenSettings)super.clone();
        clone.myGeneralSettings = ObjectUtils.doIfNotNull(myGeneralSettings, MavenGeneralSettings::clone);
        clone.myRunnerSettings = ObjectUtils.doIfNotNull(myRunnerSettings, MavenRunnerSettings::clone);
        clone.myRunnerParameters = ObjectUtils.doIfNotNull(myRunnerParameters, MavenRunnerParameters::clone);
        return clone;
      }
      catch (CloneNotSupportedException e) {
        throw new Error(e);
      }
    }

    public void readExternal(@NotNull Element element) {
      Element mavenSettingsElement = element.getChild(TAG);
      if (mavenSettingsElement != null) {
        MavenSettings settings = XmlSerializer.deserialize(mavenSettingsElement, MavenSettings.class);
        if (settings.myRunnerParameters == null) {
          settings.myRunnerParameters = new MavenRunnerParameters();
        }

        // fix old settings format
        settings.myRunnerParameters.fixAfterLoadingFromOldFormat();

        myRunnerParameters = settings.myRunnerParameters;
        myGeneralSettings = settings.myGeneralSettings;
        myRunnerSettings = settings.myRunnerSettings;
      }
    }

    public void writeExternal(@NotNull Element element) throws WriteExternalException {
      element.addContent(XmlSerializer.serialize(this));
    }
  }

  private static class ExecRemoteConnectionCreator implements RemoteConnectionCreator {

    private static final Pattern EXEC_MAVEN_PLUGIN_PATTERN = Pattern.compile("org[.]codehaus[.]mojo:exec-maven-plugin(:[\\d.]+)?:exec");

    private final JavaParameters myJavaParameters;
    private final MavenRunConfiguration myRunConfiguration;

    ExecRemoteConnectionCreator(JavaParameters javaParameters, MavenRunConfiguration runConfiguration) {
      myJavaParameters = javaParameters;
      myRunConfiguration = runConfiguration;
    }

    @Nullable
    @Override
    public RemoteConnection createRemoteConnection(ExecutionEnvironment environment) {
      ParametersList programParametersList = myJavaParameters.getProgramParametersList();
      boolean execGoal = programParametersList.getList().stream()
        .anyMatch(parameter -> parameter.equals("exec:exec") || EXEC_MAVEN_PLUGIN_PATTERN.matcher(parameter).matches());
      if (!execGoal) {
        return null;
      }

      Project project = myRunConfiguration.getProject();
      MavenRunnerParameters runnerParameters = myRunConfiguration.getRunnerParameters();

      JavaParameters parameters = new JavaParameters();
      RemoteConnection connection;
      try {
        // there's no easy and reliable way to know the version of target JRE, but without it there won't be any debugger agent settings
        parameters.setJdk(JavaParametersUtil.createProjectJdk(project, null));
        connection = new RemoteConnectionBuilder(false, DebuggerSettings.getInstance().getTransport(), "")
          .asyncAgent(true)
          .project(environment.getProject())
          .create(parameters);
      }
      catch (ExecutionException e) {
        throw new RuntimeException("Cannot create debug connection", e);
      }

      String execArgsStr;

      String execArgsPrefix = "-Dexec.args=";
      int execArgsIndex = ContainerUtil.indexOf(programParametersList.getList(), s -> s.startsWith(execArgsPrefix));
      if (execArgsIndex != -1) {
        execArgsStr = programParametersList.get(execArgsIndex).substring(execArgsPrefix.length());
      }
      else {
        execArgsStr = getExecArgsFromPomXml(project, runnerParameters);
      }

      ParametersList execArgs = new ParametersList();
      execArgs.addAll(MavenApplicationConfigurationExecutionEnvironmentProvider.patchVmParameters(parameters.getVMParametersList()));

      execArgs.addParametersString(execArgsStr);

      String classPath = FileUtil.toSystemDependentName(parameters.getClassPath().getPathsString());
      if (StringUtil.isNotEmpty(classPath)) {
        appendToClassPath(execArgs, classPath);
      }

      String execArgsCommandLineArg = execArgsPrefix + execArgs.getParametersString();
      if (execArgsIndex != -1) {
        programParametersList.set(execArgsIndex, execArgsCommandLineArg);
      }
      else {
        programParametersList.add(execArgsCommandLineArg);
      }

      return connection;
    }

    @Override
    public boolean isPollConnection() {
      return true;
    }

    private static String getExecArgsFromPomXml(Project project, MavenRunnerParameters runnerParameters) {
      VirtualFile workingDir = VfsUtil.findFileByIoFile(runnerParameters.getWorkingDirFile(), false);
      if (workingDir != null) {
        String pomFileName = StringUtil.defaultIfEmpty(runnerParameters.getPomFileName(), MavenConstants.POM_XML);
        VirtualFile pomFile = workingDir.findChild(pomFileName);
        if (pomFile != null) {
          MavenDomProjectModel projectModel = MavenDomUtil.getMavenDomProjectModel(project, pomFile);
          if (projectModel != null) {
            return StringUtil.notNullize(MavenPropertyResolver.resolve("${exec.args}", projectModel));
          }
        }
      }
      return "";
    }

    private static void appendToClassPath(ParametersList execArgs, String classPath) {
      List<String> execArgsList = execArgs.getList();
      int classPathIndex = execArgsList.indexOf("-classpath");
      if (classPathIndex == -1) {
        classPathIndex = execArgsList.indexOf("-cp");
      }
      if (classPathIndex == -1) {
        execArgs.prependAll("-classpath", "%classpath" + File.pathSeparator + classPath);
      }
      else if (classPathIndex + 1 == execArgsList.size()) { // invalid command line, but we still have to patch it
        execArgs.add("%classpath" + File.pathSeparator + classPath);
      }
      else {
        String oldClassPath = execArgs.get(classPathIndex + 1);
        execArgs.set(classPathIndex + 1, oldClassPath + File.pathSeparator + classPath);
      }
    }
  }

  protected static class MavenCommandLineState extends JavaCommandLineState implements RemoteConnectionCreator {

    private final MavenRunConfiguration myConfiguration;
    private RemoteConnectionCreator myRemoteConnectionCreator;

    protected MavenCommandLineState(@NotNull ExecutionEnvironment environment, @NotNull MavenRunConfiguration configuration) {
      super(environment);
      myConfiguration = configuration;
    }

    @Override
    public TargetEnvironmentRequest createCustomTargetEnvironmentRequest() {
      try {
        JavaParameters parameters = getJavaParameters();

        WslTargetEnvironmentConfiguration config = checkCreateWslConfiguration(parameters.getJdk());
        if (config == null || config.getDistribution() == null) {
          return null;
        }

        MavenDistribution mavenDistribution =
          MavenDistributionsCache.getInstance(myConfiguration.getProject()).getMavenDistribution(myConfiguration.getRunnerParameters()
                                                                                                   .getWorkingDirPath());
        String mavenHome = StringUtil.notNullize(config.getDistribution().getWslPath(mavenDistribution.getMavenHome().toString()));
        String mavenVersion = StringUtil.notNullize(mavenDistribution.getVersion());

        MavenRuntimeTargetConfiguration mavenConfig = new MavenRuntimeTargetConfiguration();
        mavenConfig.setHomePath(mavenHome);
        mavenConfig.setVersionString(mavenVersion);
        config.addLanguageRuntime(mavenConfig);

        return new WslTargetEnvironmentRequest(config);
      }
      catch (ExecutionException e) {
        // ignore
      }
      return null;
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

    public ExecutionResult doDelegateBuildExecute(@NotNull Executor executor,
                                                  @NotNull ProgramRunner runner,
                                                  ExternalSystemTaskId taskId,
                                                  DefaultBuildDescriptor descriptor,
                                                  ProcessHandler processHandler,
                                                  Function<String, String> targetFileMapper) throws ExecutionException {
      ConsoleView consoleView = super.createConsole(executor);
      BuildViewManager viewManager = getEnvironment().getProject().getService(BuildViewManager.class);
      descriptor.withProcessHandler(new MavenBuildHandlerFilterSpyWrapper(processHandler), null);
      descriptor.withExecutionEnvironment(getEnvironment());
      StartBuildEventImpl startBuildEvent = new StartBuildEventImpl(descriptor, "");
      boolean withResumeAction = MavenResumeAction.isApplicable(getEnvironment().getProject(), getJavaParameters(), myConfiguration);
      MavenBuildEventProcessor eventProcessor =
        new MavenBuildEventProcessor(myConfiguration, viewManager, descriptor, taskId,
                                     targetFileMapper, getStartBuildEventSupplier(runner, processHandler, startBuildEvent, withResumeAction)
        );

      processHandler.addProcessListener(new BuildToolConsoleProcessAdapter(eventProcessor, true));
      return new DefaultExecutionResult(consoleView, processHandler, new DefaultActionGroup());
    }

    public ExecutionResult doRunExecute(@NotNull Executor executor,
                                        @NotNull ProgramRunner runner,
                                        ExternalSystemTaskId taskId,
                                        DefaultBuildDescriptor descriptor,
                                        ProcessHandler processHandler,
                                        @NotNull Function<String, String> targetFileMapper) throws ExecutionException {
      final BuildView buildView = createBuildView(executor, taskId, descriptor);


      if (buildView == null) {
        MavenLog.LOG.warn("buildView is null for " + myConfiguration.getName());
      }
      MavenBuildEventProcessor eventProcessor =
        new MavenBuildEventProcessor(myConfiguration, buildView, descriptor, taskId, targetFileMapper, ctx ->
          new StartBuildEventImpl(descriptor, ""));

      processHandler.addProcessListener(new BuildToolConsoleProcessAdapter(eventProcessor, true));
      buildView.attachToProcess(new MavenHandlerFilterSpyWrapper(processHandler));

      AnAction[] actions = buildView != null ?
                           new AnAction[]{BuildTreeFilters.createFilteringActionsGroup(buildView)} : AnAction.EMPTY_ARRAY;
      DefaultExecutionResult res = new DefaultExecutionResult(buildView, processHandler, actions);
      if (MavenResumeAction.isApplicable(getEnvironment().getProject(), getJavaParameters(), myConfiguration)) {
        MavenResumeAction resumeAction =
          new MavenResumeAction(res.getProcessHandler(), runner, getEnvironment(), eventProcessor.getParsingContext());
        res.setRestartActions(resumeAction);
      }
      return res;
    }

    @NotNull
    private Function<MavenParsingContext, StartBuildEvent> getStartBuildEventSupplier(@NotNull ProgramRunner runner,
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

    @NotNull
    @Override
    public ExecutionResult execute(@NotNull Executor executor, @NotNull ProgramRunner<?> runner) throws ExecutionException {
      checkMavenWrapperAndPatchJavaParams();
      final ProcessHandler processHandler = startProcess();
      ExecutionEnvironment environment = getEnvironment();
      TargetEnvironment targetEnvironment = environment.getPreparedTargetEnvironment(this, TargetProgressIndicator.EMPTY);
      Function<String, String> targetFileMapper = path -> {
        return path != null && SystemInfo.isWindows && path.charAt(0) == '/' ? path.substring(1) : path;
      };
      if (!(targetEnvironment instanceof LocalTargetEnvironment)) {
        TargetEnvironmentRequest targetEnvironmentRequest = getTargetEnvironmentRequest();
        LanguageRuntimeType.VolumeType mavenProjectFolderVolumeType = MavenRuntimeType.getPROJECT_FOLDER_VOLUME().getType();
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
      ExternalSystemTaskId taskId = ExternalSystemTaskId.create(MavenUtil.SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, myConfiguration.getProject());
      DefaultBuildDescriptor descriptor =
        new DefaultBuildDescriptor(taskId, myConfiguration.getName(), workingDir, System.currentTimeMillis());
      if (MavenRunConfigurationType.isDelegate(getEnvironment())) {
        return doDelegateBuildExecute(executor, runner, taskId, descriptor, processHandler, targetFileMapper);
      }
      else {
        return doRunExecute(executor, runner, taskId, descriptor, processHandler, targetFileMapper);
      }
    }

    @Nullable
    private BuildView createBuildView(@NotNull Executor executor, @NotNull ExternalSystemTaskId taskId,
                                      @NotNull BuildDescriptor descriptor) throws ExecutionException {
      ConsoleView console = super.createConsole(executor);
      if (console == null) {
        return null;
      }
      Project project = myConfiguration.getProject();
      ExternalSystemRunConfigurationViewManager viewManager = project.getService(ExternalSystemRunConfigurationViewManager.class);
      return new BuildView(project, console, descriptor, "build.toolwindow.run.selection.state",
                           viewManager) {
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
        return super.createTargetedCommandLine(request);
      }
      if (request.getConfiguration() == null) {
        throw new CantRunException(RunnerBundle.message("cannot.find.target.environment.configuration"));
      }
      var settings = new MavenSettings(myConfiguration.getProject());
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
      if (environment instanceof LocalTargetEnvironment) {
        super.handleCreatedTargetEnvironment(environment, targetProgressIndicator);
      }
      else {
        TargetedCommandLineBuilder targetedCommandLineBuilder = getTargetedCommandLine();
        Objects.requireNonNull(targetedCommandLineBuilder.getUserData(MavenCommandLineSetup.getSetupKey()))
          .provideEnvironment(environment, targetProgressIndicator);
      }
    }

    @NotNull
    @Override
    protected OSProcessHandler startProcess() throws ExecutionException {
      ExecutionEnvironment environment = getEnvironment();
      TargetEnvironment remoteEnvironment = environment.getPreparedTargetEnvironment(this, TargetProgressIndicator.EMPTY);
      TargetedCommandLineBuilder targetedCommandLineBuilder = getTargetedCommandLine();
      TargetedCommandLine targetedCommandLine = targetedCommandLineBuilder.build();
      Process process = remoteEnvironment.createProcess(targetedCommandLine, new EmptyProgressIndicator());
      OSProcessHandler handler = new KillableColoredProcessHandler.Silent(process,
                                                                          targetedCommandLine.getCommandPresentation(remoteEnvironment),
                                                                          targetedCommandLine.getCharset(),
                                                                          targetedCommandLineBuilder.getFilesToDeleteOnTermination());
      ProcessTerminatedListener.attach(handler);
      JavaRunConfigurationExtensionManager.getInstance()
        .attachExtensionsToProcess(myConfiguration, handler, getRunnerSettings());
      return handler;
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

    @Nullable
    @Override
    public RemoteConnection createRemoteConnection(ExecutionEnvironment environment) {
      return getRemoteConnectionCreator().createRemoteConnection(environment);
    }

    @Override
    public boolean isPollConnection() {
      return getRemoteConnectionCreator().isPollConnection();
    }
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

  public static class MavenHandlerFilterSpyWrapper extends ProcessHandler {
    private final ProcessHandler myOriginalHandler;

    MavenHandlerFilterSpyWrapper(ProcessHandler original) {

      myOriginalHandler = original;
    }

    @Override
    public void detachProcess() {
      myOriginalHandler.detachProcess();
    }

    @Override
    public boolean isProcessTerminated() {
      return myOriginalHandler.isProcessTerminated();
    }

    @Override
    public boolean isProcessTerminating() {
      return myOriginalHandler.isProcessTerminating();
    }

    @Nullable
    @Override
    public Integer getExitCode() {
      return myOriginalHandler.getExitCode();
    }

    @Override
    protected void destroyProcessImpl() {
      myOriginalHandler.destroyProcess();
    }

    @Override
    protected void detachProcessImpl() {
      myOriginalHandler.detachProcess();
    }

    @Override
    public boolean detachIsDefault() {
      return myOriginalHandler.detachIsDefault();
    }

    @Nullable
    @Override
    public OutputStream getProcessInput() {
      return myOriginalHandler.getProcessInput();
    }

    @Override
    public void addProcessListener(@NotNull ProcessListener listener) {
      myOriginalHandler.addProcessListener(filtered(listener));
    }

    @Override
    public void addProcessListener(@NotNull final ProcessListener listener, @NotNull Disposable parentDisposable) {
      myOriginalHandler.addProcessListener(filtered(listener), parentDisposable);
    }

    private ProcessListener filtered(ProcessListener listener) {
      return new ProcessListenerWithFilteredSpyOutput(listener, this);
    }
  }

  /* this class is needede to implement build process handler and support running delegate builds*/
  public static class MavenBuildHandlerFilterSpyWrapper extends BuildProcessHandler {
    private final ProcessHandler myOriginalHandler;

    public MavenBuildHandlerFilterSpyWrapper(ProcessHandler original) {
      myOriginalHandler = original;
    }


    @Override
    public void destroyProcess() {
      myOriginalHandler.destroyProcess();
    }

    @Override
    public void detachProcess() {
      myOriginalHandler.detachProcess();
    }

    @Override
    public boolean isProcessTerminated() {
      return myOriginalHandler.isProcessTerminated();
    }

    @Override
    public boolean isProcessTerminating() {
      return myOriginalHandler.isProcessTerminating();
    }

    @Nullable
    @Override
    public Integer getExitCode() {
      return myOriginalHandler.getExitCode();
    }

    @Override
    public String getExecutionName() {
      return "Maven build";
    }

    @Override
    protected void destroyProcessImpl() {
      myOriginalHandler.destroyProcess();
    }

    @Override
    protected void detachProcessImpl() {
      myOriginalHandler.detachProcess();
    }

    @Override
    public boolean detachIsDefault() {
      return myOriginalHandler.detachIsDefault();
    }

    @Nullable
    @Override
    public OutputStream getProcessInput() {
      return myOriginalHandler.getProcessInput();
    }

    @Override
    public void addProcessListener(@NotNull ProcessListener listener) {
      myOriginalHandler.addProcessListener(filtered(listener));
    }

    @Override
    public void addProcessListener(@NotNull final ProcessListener listener, @NotNull Disposable parentDisposable) {
      myOriginalHandler.addProcessListener(filtered(listener), parentDisposable);
    }

    private ProcessListener filtered(ProcessListener listener) {
      return new ProcessListenerWithFilteredSpyOutput(listener, this);
    }
  }

  public static class ProcessListenerWithFilteredSpyOutput implements ProcessListener {
    private final ProcessListener myListener;
    private final ProcessHandler myProcessHandler;
    private final MavenSimpleConsoleEventsBuffer mySimpleConsoleEventsBuffer;

    ProcessListenerWithFilteredSpyOutput(ProcessListener listener, ProcessHandler processHandler) {
      myListener = listener;
      myProcessHandler = processHandler;
      mySimpleConsoleEventsBuffer = new MavenSimpleConsoleEventsBuffer(
        (l, k) -> myListener.onTextAvailable(new ProcessEvent(processHandler, l), k),
        Registry.is("maven.spy.events.debug")
      );
    }

    @Override
    public void startNotified(@NotNull ProcessEvent event) {
      myListener.startNotified(event);
    }

    @Override
    public void processTerminated(@NotNull ProcessEvent event) {
      myListener.processTerminated(event);
    }

    @Override
    public void processWillTerminate(@NotNull ProcessEvent event, boolean willBeDestroyed) {
      myListener.processWillTerminate(event, willBeDestroyed);
    }

    @Override
    public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
      mySimpleConsoleEventsBuffer.addText(event.getText(), outputType);
    }
  }
}
