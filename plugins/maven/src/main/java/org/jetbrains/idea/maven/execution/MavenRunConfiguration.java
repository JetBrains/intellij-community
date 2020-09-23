// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution;

import com.intellij.CommonBundle;
import com.intellij.build.*;
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
import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.options.SettingsEditorGroup;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.buildtool.BuildToolConsoleProcessAdapter;
import org.jetbrains.idea.maven.buildtool.MavenBuildEventProcessor;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.MavenPropertyResolver;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.execution.target.MavenCommandLineSetup;
import org.jetbrains.idea.maven.execution.target.MavenRuntimeTargetConfiguration;
import org.jetbrains.idea.maven.execution.target.MavenRuntimeType;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenParsingContext;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.project.MavenGeneralSettingsEditor;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.File;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

import static com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType.EXECUTE_TASK;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static com.intellij.openapi.util.text.StringUtil.*;
import static com.intellij.util.containers.ContainerUtil.indexOf;
import static org.jetbrains.idea.maven.execution.MavenApplicationConfigurationExecutionEnvironmentProvider.patchVmParameters;

public class MavenRunConfiguration extends LocatableConfigurationBase implements ModuleRunProfile, TargetEnvironmentAwareRunProfile {
  private MavenSettings mySettings;

  protected MavenRunConfiguration(Project project, ConfigurationFactory factory, String name) {
    super(project, factory, name);
    mySettings = new MavenSettings(project);
  }

  @Override
  public MavenRunConfiguration clone() {
    MavenRunConfiguration clone = (MavenRunConfiguration)super.clone();
    clone.mySettings = mySettings.clone();
    return clone;
  }

  @NotNull
  @Override
  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    SettingsEditorGroup<MavenRunConfiguration> group = new SettingsEditorGroup<>();

    group.addEditor(RunnerBundle.message("maven.runner.parameters.title"), new MavenRunnerParametersSettingEditor(getProject()));

    group.addEditor(CommonBundle.message("tab.title.general"), new MavenGeneralSettingsEditor(getProject()));
    group.addEditor(RunnerBundle.message("maven.tab.runner"), new MavenRunnerSettingsEditor(getProject()));
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
    return MavenExternalParameters
      .createJavaParameters(project, mySettings.myRunnerParameters, mySettings.myGeneralSettings, mySettings.myRunnerSettings, this);
  }

  @Override
  public RunProfileState getState(@NotNull final Executor executor, @NotNull final ExecutionEnvironment env) {
    return new JavaCommandLineStateImpl(env, getName());
  }

  @NotNull
  public RemoteConnectionCreator createRemoteConnectionCreator(JavaParameters javaParameters) {
    return new ExecRemoteConnectionCreator(javaParameters, this);
  }

  private void updateProjectsFolders() {
    MavenProjectsManager.getInstance(getProject()).updateProjectTargetFolders();
  }

  @Nullable
  public MavenGeneralSettings getGeneralSettings() {
    return mySettings.myGeneralSettings;
  }

  public void setGeneralSettings(@Nullable MavenGeneralSettings settings) {
    mySettings.myGeneralSettings = settings;
  }

  @Nullable
  public MavenRunnerSettings getRunnerSettings() {
    return mySettings.myRunnerSettings;
  }

  public void setRunnerSettings(@Nullable MavenRunnerSettings settings) {
    mySettings.myRunnerSettings = settings;
  }

  public MavenRunnerParameters getRunnerParameters() {
    return mySettings.myRunnerParameters;
  }

  public void setRunnerParameters(MavenRunnerParameters p) {
    mySettings.myRunnerParameters = p;
  }

  @Override
  public void readExternal(@NotNull Element element) throws InvalidDataException {
    super.readExternal(element);

    Element mavenSettingsElement = element.getChild(MavenSettings.TAG);
    if (mavenSettingsElement != null) {
      mySettings = XmlSerializer.deserialize(mavenSettingsElement, MavenSettings.class);

      if (mySettings.myRunnerParameters == null) mySettings.myRunnerParameters = new MavenRunnerParameters();

      // fix old settings format
      mySettings.myRunnerParameters.fixAfterLoadingFromOldFormat();
    }
  }

  @Override
  public void writeExternal(@NotNull Element element) throws WriteExternalException {
    super.writeExternal(element);
    element.addContent(XmlSerializer.serialize(mySettings));
  }

  @Override
  public String suggestedName() {
    return MavenRunConfigurationType.generateName(getProject(), mySettings.myRunnerParameters);
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

  public static class MavenSettings implements Cloneable {
    public static final String TAG = "MavenSettings";

    public MavenGeneralSettings myGeneralSettings;
    public MavenRunnerSettings myRunnerSettings;
    public MavenRunnerParameters myRunnerParameters;

    /* reflection only */
    public MavenSettings() {
    }

    public MavenSettings(Project project) {
      this(null, null, new MavenRunnerParameters());
    }

    private MavenSettings(@Nullable MavenGeneralSettings cs, @Nullable MavenRunnerSettings rs, MavenRunnerParameters rp) {
      myGeneralSettings = cs == null ? null : cs.clone();
      myRunnerSettings = rs == null ? null : rs.clone();
      myRunnerParameters = rp.clone();
    }

    @Override
    protected MavenSettings clone() {
      return new MavenSettings(myGeneralSettings, myRunnerSettings, myRunnerParameters);
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
      boolean execGoal = programParametersList.getList().stream().anyMatch(parameter ->
                                                                             parameter.equals("exec:exec") ||
                                                                             EXEC_MAVEN_PLUGIN_PATTERN.matcher(parameter).matches()
      );
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
          .memoryAgent(DebuggerSettings.getInstance().ENABLE_MEMORY_AGENT)
          .create(parameters);
      }
      catch (ExecutionException e) {
        throw new RuntimeException("Cannot create debug connection", e);
      }

      String execArgsStr;

      String execArgsPrefix = "-Dexec.args=";
      int execArgsIndex = indexOf(programParametersList.getList(), s -> s.startsWith(execArgsPrefix));
      if (execArgsIndex != -1) {
        execArgsStr = programParametersList.get(execArgsIndex).substring(execArgsPrefix.length());
      }
      else {
        execArgsStr = getExecArgsFromPomXml(project, runnerParameters);
      }

      ParametersList execArgs = new ParametersList();
      execArgs.addAll(patchVmParameters(parameters.getVMParametersList()));

      execArgs.addParametersString(execArgsStr);

      String classPath = toSystemDependentName(parameters.getClassPath().getPathsString());
      if (isNotEmpty(classPath)) {
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
        String pomFileName = defaultIfEmpty(runnerParameters.getPomFileName(), MavenConstants.POM_XML);
        VirtualFile pomFile = workingDir.findChild(pomFileName);
        if (pomFile != null) {
          MavenDomProjectModel projectModel = MavenDomUtil.getMavenDomProjectModel(project, pomFile);
          if (projectModel != null) {
            return notNullize(MavenPropertyResolver.resolve("${exec.args}", projectModel));
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

  private class JavaCommandLineStateImpl extends JavaCommandLineState implements RemoteConnectionCreator {

    @NlsSafe private final String myName;
    private RemoteConnectionCreator myRemoteConnectionCreator;

    protected JavaCommandLineStateImpl(@NotNull ExecutionEnvironment environment, @NlsSafe String name) {
      super(environment);
      myName = name;
    }

    @Override
    protected JavaParameters createJavaParameters() throws ExecutionException {
      TargetEnvironmentRequest targetEnvironmentRequest = getTargetEnvironmentRequest();
      if (targetEnvironmentRequest == null || targetEnvironmentRequest instanceof LocalTargetEnvironmentRequest) {
        return MavenRunConfiguration.this.createJavaParameters(getEnvironment().getProject());
      } else {
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
      BuildViewManager viewManager = ServiceManager.getService(getEnvironment().getProject(), BuildViewManager.class);
      descriptor.withProcessHandler(new MavenBuildHandlerFilterSpyWrapper(processHandler), null);
      descriptor.withExecutionEnvironment(getEnvironment());
      StartBuildEventImpl startBuildEvent = new StartBuildEventImpl(descriptor, "");
      boolean withResumeAction =
        MavenResumeAction.isApplicable(getEnvironment().getProject(), getJavaParameters(), MavenRunConfiguration.this);
      MavenBuildEventProcessor eventProcessor =
        new MavenBuildEventProcessor(getProject(), getProject().getBasePath(), viewManager, descriptor, taskId,
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
        MavenLog.LOG.warn("buildView is null for " + myName);
      }
      MavenBuildEventProcessor eventProcessor =
        new MavenBuildEventProcessor(getProject(), getProject().getBasePath(), buildView, descriptor, taskId, targetFileMapper, ctx ->
          new StartBuildEventImpl(descriptor, ""));

      processHandler.addProcessListener(new BuildToolConsoleProcessAdapter(eventProcessor, true));
      buildView.attachToProcess(new MavenHandlerFilterSpyWrapper(processHandler));

      AnAction[] actions = buildView != null ?
                           new AnAction[]{BuildTreeFilters.createFilteringActionsGroup(buildView)} : AnAction.EMPTY_ARRAY;
      DefaultExecutionResult res = new DefaultExecutionResult(buildView, processHandler, actions);
      if (MavenResumeAction.isApplicable(getEnvironment().getProject(), getJavaParameters(), MavenRunConfiguration.this)) {
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
      final ProcessHandler processHandler = startProcess();
      EmptyProgressIndicator environmentIndicator = new EmptyProgressIndicator();
      ExecutionEnvironment environment = getEnvironment();
      TargetEnvironment targetEnvironment = environment.getPreparedTargetEnvironment(this, environmentIndicator);
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
            targetFileMapper = path -> {
              if (path == null) return null;
              boolean isWindows = targetEnvironment.getTargetPlatform().getPlatform() == Platform.WINDOWS;
              path = isWindows && path.charAt(0) == '/' ? path.substring(1) : path;
              if (path.startsWith(targetPath)) {
                return Paths.get(localPath, trimStart(path, targetPath)).toString();
              }
              // workaround for "var -> private/var" symlink
              // TODO target absolute path can be used instead for such mapping of target file absolute paths
              if (path.startsWith("/private" + targetPath)) {
                return Paths.get(localPath, trimStart(path, "/private" + targetPath)).toString();
              }
              return path;
            };
            break;
          }
        }
      }

      TargetedCommandLineBuilder targetedCommandLineBuilder = getTargetedCommandLine();
      String targetWorkingDirectory = targetedCommandLineBuilder.build().getWorkingDirectory();
      String workingDir =
        targetWorkingDirectory != null ? targetFileMapper.apply(targetWorkingDirectory) : getEnvironment().getProject().getBasePath();
      ExternalSystemTaskId taskId = ExternalSystemTaskId.create(MavenUtil.SYSTEM_ID, EXECUTE_TASK, getProject());
      DefaultBuildDescriptor descriptor = new DefaultBuildDescriptor(taskId, myName, workingDir, System.currentTimeMillis());
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
      return new BuildView(getProject(), console, descriptor, "build.toolwindow.run.selection.state",
                           new ViewManager() {
                             @Override
                             public boolean isConsoleEnabledByDefault() {
                               return true;
                             }

                             @Override
                             public boolean isBuildContentView() {
                               return true;
                             }
                           }) {
        @Override
        public void dispose() {
          super.dispose();
        }
      };
    }

    @Override
    protected @NotNull TargetedCommandLineBuilder createTargetedCommandLine(@NotNull TargetEnvironmentRequest request,
                                                                            @Nullable TargetEnvironmentConfiguration configuration)
      throws ExecutionException {
      if (request instanceof LocalTargetEnvironmentRequest) {
        return super.createTargetedCommandLine(request, configuration);
      }
      if (configuration == null) {
        throw new CantRunException("Cannot find target environment configuration");
      }
      return new MavenCommandLineSetup(getProject(), myName, request, configuration)
        .setupCommandLine(mySettings)
        .getCommandLine();
    }

    @Override
    public void handleCreatedTargetEnvironment(@NotNull TargetEnvironment environment, @NotNull ProgressIndicator progressIndicator) {
      if (environment instanceof LocalTargetEnvironment) {
        super.handleCreatedTargetEnvironment(environment, progressIndicator);
      }
      else {
        TargetedCommandLineBuilder targetedCommandLineBuilder = getTargetedCommandLine();
        Objects.requireNonNull(targetedCommandLineBuilder.getUserData(MavenCommandLineSetup.getSetupKey()))
          .provideEnvironment(environment, progressIndicator);
      }
    }

    @NotNull
    @Override
    protected OSProcessHandler startProcess() throws ExecutionException {
      EmptyProgressIndicator environmentIndicator = new EmptyProgressIndicator();
      ExecutionEnvironment environment = getEnvironment();
      TargetEnvironment remoteEnvironment = environment.getPreparedTargetEnvironment(this, environmentIndicator);
      TargetedCommandLineBuilder targetedCommandLineBuilder = getTargetedCommandLine();
      TargetedCommandLine targetedCommandLine = targetedCommandLineBuilder.build();
      Process process = remoteEnvironment.createProcess(targetedCommandLine, new EmptyProgressIndicator());
      OSProcessHandler handler = new KillableColoredProcessHandler.Silent(process,
                                                                          targetedCommandLine.getCommandPresentation(remoteEnvironment),
                                                                          targetedCommandLine.getCharset(),
                                                                          targetedCommandLineBuilder.getFilesToDeleteOnTermination());
      ProcessTerminatedListener.attach(handler);
      JavaRunConfigurationExtensionManager.getInstance()
        .attachExtensionsToProcess(MavenRunConfiguration.this, handler, getRunnerSettings());
      return handler;
    }

    public RemoteConnectionCreator getRemoteConnectionCreator() {
      if (myRemoteConnectionCreator == null) {
        try {
          myRemoteConnectionCreator = MavenRunConfiguration.this.createRemoteConnectionCreator(getJavaParameters());
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
