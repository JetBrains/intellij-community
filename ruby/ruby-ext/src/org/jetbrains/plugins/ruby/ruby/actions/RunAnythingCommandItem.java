package org.jetbrains.plugins.ruby.ruby.actions;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.KillableColoredProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathMappingSettings;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.execution.ParametersListUtil;
import com.intellij.util.net.NetUtils;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugProcessStarter;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import icons.RubyIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.RBundle;
import org.jetbrains.plugins.ruby.gem.GemsDataKeys;
import org.jetbrains.plugins.ruby.remote.RubyRemoteInterpreterManager;
import org.jetbrains.plugins.ruby.ruby.RModuleUtil;
import org.jetbrains.plugins.ruby.ruby.RubyUtil;
import org.jetbrains.plugins.ruby.ruby.debugger.*;
import org.jetbrains.plugins.ruby.ruby.debugger.impl.LocalPositionConverter;
import org.jetbrains.plugins.ruby.ruby.debugger.impl.RubyDebugProcess;
import org.jetbrains.plugins.ruby.ruby.debugger.settings.RubyDebuggerSettings;
import org.jetbrains.plugins.ruby.ruby.run.PortForwarding;
import org.jetbrains.plugins.ruby.ruby.run.RubyAbstractRunner;
import org.jetbrains.plugins.ruby.ruby.run.RubyLocalRunner;
import org.jetbrains.plugins.ruby.ruby.run.configuration.DebugGemHelper;
import org.jetbrains.plugins.ruby.ruby.run.configuration.RubyAbstractCommandLineState;
import org.jetbrains.plugins.ruby.ruby.sdk.RubySdkAdditionalData;
import org.jetbrains.plugins.ruby.ruby.sdk.RubySdkUtil;
import org.jetbrains.plugins.ruby.rvm.RVMSupportUtil;
import org.jetbrains.plugins.ruby.testing.testunit.runConfigurations.RakeRunnerConstants;
import org.jetbrains.plugins.ruby.utils.OSUtil;
import org.jetbrains.plugins.ruby.version.management.rbenv.gemsets.RbenvGemsetManager;
import org.rubyforge.debugcommons.RubyDebuggerProxy;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.*;
import java.util.List;
import com.intellij.execution.configurations.CommandLineState;
import com.intellij.execution.configurations.RunProfileState;

public class RunAnythingCommandItem extends RunAnythingItem<String> {
  private static final Logger LOG = Logger.getInstance(RunAnythingCommandItem.class);
  @Nullable private final Module myModule;
  @NotNull private final String myCommandLine;
  @NotNull private final Project myProject;
  static final Icon UNDEFINED_COMMAND_ICON = RubyIcons.RunAnything.Run_anything;

  public RunAnythingCommandItem(@NotNull Project project, @Nullable Module module, @NotNull String commandLine) {
    myProject = project;
    myModule = module;
    myCommandLine = commandLine;
  }

  @Override
  public void runInner(@NotNull Executor executor,
                       @Nullable VirtualFile workDirectory,
                       @Nullable Component component,
                       @NotNull Project project,
                       @Nullable AnActionEvent event) {
    runCommand(workDirectory, project, myCommandLine, myModule, executor);
  }

  public static void runCommand(@Nullable VirtualFile workDirectory,
                                @NotNull Project project,
                                @NotNull String commandString,
                                @Nullable Module module,
                                @NotNull Executor executor) {
    Collection<String> commands = RunAnythingCache.getInstance(project).getState().undefinedCommands;
    commands.remove(commandString);
    commands.add(commandString);

    Sdk sdk = RModuleUtil.getInstance().findRubySdkForModule(module);

    GeneralCommandLine commandLine = new GeneralCommandLine().withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE);

    String command = commandString;
    Map<String, String> env = ContainerUtil.newHashMap(commandLine.getEffectiveEnvironment());
    if (sdk != null && !RubyRemoteInterpreterManager.getInstance().isRemoteSdk(sdk)) {
      if (RVMSupportUtil.isRVMInterpreter(sdk)) {
        command = getRVMAwareCommand(sdk, commandString, project);
      }
      else if (RbenvGemsetManager.isRbenvSdk(sdk)) {
        command = getRbenvAwareCommand(sdk, env, commandString, project, module);
      }
      else {
        command = getRubyAwareCommand(sdk, env, commandString);
      }
    }

    List<String> shellCommand = ContainerUtil.newArrayList(getShellCommand());
    if (shellCommand.isEmpty()) {
      shellCommand = ParametersListUtil.parse(command, false, true);
    }
    else {
      shellCommand.add(command);
    }

    commandLine = new GeneralCommandLine(shellCommand)
      .withEnvironment(env)
      .withWorkDirectory(RunAnythingItem.getActualWorkDirectory(project, workDirectory));

    HashMap<String, Object> dataMap = new HashMap<>();
    dataMap.put(LangDataKeys.MODULE.getName(), module);
    dataMap.put(GemsDataKeys.SDK.getName(), sdk);
    runInConsole(project, commandLine, executor, SimpleDataContext.getSimpleContext(dataMap, DataContext.EMPTY_CONTEXT));
  }

  private static void runInConsole(@NotNull Project project,
                                   @NotNull GeneralCommandLine commandLine,
                                   @NotNull Executor executor,
                                   @NotNull DataContext dataContext) {
    try {
      ExecutionEnvironment environment = ExecutionEnvironmentBuilder.create(project, executor, new AnythingRunProfile(project, commandLine))
                                                                    .dataContext(dataContext)
                                                                    .build();

      environment.getRunner().execute(environment);
    }
    catch (ExecutionException e) {
      LOG.warn(e);
      Messages.showInfoMessage(project, e.getMessage(), RBundle.message("run.anything.console.error.title"));
    }
  }

  @NotNull
  private static List<String> getShellCommand() {
    if (SystemInfoRt.isWindows) return ContainerUtil.immutableList(ExecUtil.getWindowsShellName(), "/c");

    String shell = System.getenv("SHELL");
    if (shell == null || !new File(shell).canExecute()) {
      return ContainerUtil.emptyList();
    }
    else {
      List<String> shellCommands = ContainerUtil.newArrayList(shell);
      if ("/bin/bash".equals(shell)) {
        shellCommands.add("--login");
      }
      shellCommands.add("-c");
      return shellCommands;
    }
  }

  private static String getRubyAwareCommand(@NotNull Sdk sdk, @NotNull Map<String, String> env, @NotNull String commandLine) {
    VirtualFile sdkHomeDirectory = sdk.getHomeDirectory();
    if (sdkHomeDirectory == null) return commandLine;

    VirtualFile parent = sdkHomeDirectory.getParent();
    if (parent == null) return commandLine;

    final String path = FileUtil.toSystemDependentName(parent.getPath());
    final String envName = OSUtil.getPathEnvVariableName();
    final String newPath = OSUtil.prependToPathEnvVariable(env.get(envName), path);
    env.put(envName, newPath);

    return commandLine;
  }

  private static String getRbenvAwareCommand(@NotNull Sdk sdk,
                                             @NotNull Map<String, String> env,
                                             @NotNull String commandLine,
                                             @NotNull Project project,
                                             @Nullable Module module) {
    String exeCommand = commandLine.contains(" ") ? StringUtil.substringBefore(commandLine, " ") : commandLine;
    String shimsExec = RbenvGemsetManager.getShimsCommandPath(Objects.requireNonNull(exeCommand));
    if (shimsExec == null || !RunAnythingCache.getInstance(project).CAN_RUN_RBENV) return commandLine;

    RubyAbstractRunner.patchRbenvEnv(env, module, sdk);

    return shimsExec + (commandLine.contains(" ") ? " " + StringUtil.substringAfter(commandLine, " ") : "");
  }

  @NotNull
  private static String getRVMAwareCommand(@NotNull Sdk sdk, @NotNull String commandLine, @NotNull Project project) {
    if (commandLine.startsWith("rvm ")) return commandLine;

    String version = RVMSupportUtil.getRVMSdkVersion(sdk);
    String gemset = RVMSupportUtil.getGemset(sdk);

    if (version == null) return commandLine;
    if (gemset != null) version += '@' + gemset;

    if (!RunAnythingCache.getInstance(project).CAN_RUN_RVM) return commandLine;

    return "rvm " + version + " do " + commandLine;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    RunAnythingCommandItem item = (RunAnythingCommandItem)o;
    return Objects.equals(myModule, item.myModule) &&
           Objects.equals(myCommandLine, item.myCommandLine) &&
           Objects.equals(myProject, item.myProject);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myModule, myCommandLine, myProject);
  }

  @NotNull
  @Override
  public String getText() {
    return myCommandLine;
  }

  @NotNull
  @Override
  public Icon getIcon() {
    return UNDEFINED_COMMAND_ICON;
  }

  @NotNull
  @Override
  public String getValue() {
    return myCommandLine;
  }

  @Override
  public void triggerUsage() {
    RunAnythingUtil.triggerDebuggerStatistics();
  }

  @NotNull
  @Override
  public Component getComponent(boolean isSelected) {
    return RunAnythingUtil.getUndefinedCommandCellRendererComponent(this, isSelected);
  }


  private static class AnythingRunProfile implements DebuggableRunProfile {
    @NotNull
    private Project myProject;
    @NotNull
    private final GeneralCommandLine myCommandLine;

    @Nullable
    private ProcessHandler myProcessHandler;

    public AnythingRunProfile(@NotNull Project project, @NotNull GeneralCommandLine commandLine) {
      myCommandLine = commandLine;
      myProject = project;
    }

    @NotNull
    @Override
    public XDebugSession createDebugSession(@NotNull RunProfileState runProfileState, @NotNull ExecutionEnvironment environment)
      throws ExecutionException {
      if (environment.getDataContext() == null) {
        throw new ExecutionException("Module should be passed as data to ExecutionEnvironment");
      }

      final Project project = environment.getProject();
      final Module module = LangDataKeys.MODULE.getData(environment.getDataContext());

      final RubyLocalRunner rubyRunner = RubyLocalRunner.getRunner(module);
      Pair<PortForwarding, PortForwarding> pair = rubyRunner.getDebuggerForwardings();

      final int debuggerPort = pair.getFirst().getLocalPort();
      final int dispatcher = pair.getSecond().getLocalPort();

      final Sdk sdk = RModuleUtil.getInstance().findRubySdkForModule(module);
      if (sdk == null) {
        throw new ExecutionException("Can't debug without sdk");
      }

      final DebugGemHelper debugGemHelper = RubyAbstractCommandLineState.selectDebugGemHelper(sdk, module, RubyDebugMode.NORMAL_MODE);

      if (debugGemHelper.needsDebugPreLoader()) {
        applyDebugStarterToEnv(rubyRunner, debuggerPort, dispatcher, sdk, debugGemHelper);
      }

      int timeout = RubyDebuggerSettings.getInstance().getState().getTimeout();

      boolean supportsNonSuspendedFramesReading = RubyDebugRunner.supportsNonSuspendedFramesReading(debugGemHelper, sdk);
      boolean supportsCatchpointRemoval = debugGemHelper.supportsCatchpointRemoval();
      final String localHostString = NetUtils.getLocalHostString();
      final ProcessHandler serverProcessHandler = getProcessHandler();

      final RubyDebuggerProxy rubyDebuggerProxy =
        new RubyDebuggerProxy(timeout, supportsNonSuspendedFramesReading, false, supportsCatchpointRemoval);

      final RubyProcessDispatcher acceptor = RubyDebugRunner
        .getAcceptor(supportsNonSuspendedFramesReading, localHostString, supportsCatchpointRemoval, Integer.valueOf(dispatcher));

      rubyDebuggerProxy.setDebugTarget(
        RubyDebugRunner.getDebugTarget(null, serverProcessHandler, debuggerPort, rubyDebuggerProxy, localHostString));

      boolean myEnableFileFiltering = debugGemHelper.supportFileFiltering()
                                      && RubyDebuggerSettings.getInstance().getState().isStepIntoProjectOnly();
      final SourcePositionConverter converter = new LocalPositionConverter(project, !RubySdkUtil.isRuby18(sdk));

      final XDebugProcessStarter processStarter = new XDebugProcessStarter() {
        @NotNull
        public XDebugProcess start(@NotNull final XDebugSession session) {
          return new RubyDebugProcess(session, runProfileState, serverProcessHandler, rubyDebuggerProxy, timeout, converter,
                                      environment.getExecutor(), acceptor, debugGemHelper.pauseActionSupported(),
                                      false,
                                      myEnableFileFiltering, debugGemHelper.getSourceRoots(project),
                                      debugGemHelper.getExcludedDirs(project)) {
            @NotNull
            @Override
            public ExecutionConsole createConsole() {
              ConsoleView console = (ConsoleView)super.createConsole();
              console.attachToProcess(serverProcessHandler);
              return console;
            }
          };
        }
      };

      return XDebuggerManager.getInstance(project).startSession(environment, processStarter);
    }

    @Nullable
    @Override
    public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment environment) {
      return new CommandLineState(environment) {
        @NotNull
        @Override
        protected ProcessHandler startProcess() throws ExecutionException {
          return getProcessHandler();
        }
      };
    }

    @Override
    public String getName() {
      return myCommandLine.getCommandLineString();
    }

    @Nullable
    @Override
    public Icon getIcon() {
      return UNDEFINED_COMMAND_ICON;
    }

    private void applyDebugStarterToEnv(@NotNull RubyLocalRunner rubyRunner,
                                        int debuggerPort,
                                        int dispatcherPort,
                                        @NotNull Sdk sdk,
                                        @NotNull DebugGemHelper debugGemHelper) {
      final Map<String, String> env = myCommandLine.getEnvironment();
      final String gemsPath = RubyUtil.getScriptFullPath("rb/gems");
      assert gemsPath != null;

      final RubySdkAdditionalData data = RubySdkUtil.getRubySdkAdditionalData(sdk);
      PathMappingSettings mappingSettings = rubyRunner.addDefaultMappings(null);

      String rubyLib = OSUtil.prependToRUBYLIBEnvVariable(data.getSdkSystemAccessor(),
                                                          env.get(RakeRunnerConstants.RUBYLIB_ENVIRONMENT_VARIABLE),
                                                          mappingSettings.convertToRemote(gemsPath));

      env.put("RUBYMINE_DEBUG_PORT", String.valueOf(debuggerPort));
      env.put("DEBUGGER_CLI_DEBUG", String.valueOf(RubyDebuggerSettings.getInstance().getState().isVerboseOutput()));
      env.put("IDE_PROCESS_DISPATCHER", String.valueOf(dispatcherPort));
      env.put(
        RakeRunnerConstants.RUBYLIB_ENVIRONMENT_VARIABLE,
        debugGemHelper.updateRubyLib(rubyLib, data.getSdkSystemAccessor(), mappingSettings)
      );

      OSUtil.appendToEnvVariable(RubyUtil.RUBYOPT, "-rrubymine_debug_anything.rb", env, " ");
    }

    @NotNull
    private ProcessHandler getProcessHandler() throws ExecutionException {
      if (myProcessHandler != null) {
        return myProcessHandler;
      }
      myProcessHandler = new KillableColoredProcessHandler(myCommandLine) {
        @Override
        protected void notifyProcessTerminated(int exitCode) {
          RunContentDescriptor contentDescriptor
            = ExecutionManager.getInstance(myProject).getContentManager()
                              .findContentDescriptor(DefaultRunExecutor.getRunExecutorInstance(), this);

          if (contentDescriptor != null && contentDescriptor.getExecutionConsole() instanceof ConsoleView) {
            ((ConsoleView)contentDescriptor.getExecutionConsole())
              .print(RBundle.message("run.anything.console.process.finished", exitCode), ConsoleViewContentType.SYSTEM_OUTPUT);
          }
          super.notifyProcessTerminated(exitCode);
        }
      };
      ((KillableColoredProcessHandler)myProcessHandler).setHasPty(true);
      return myProcessHandler;
    }
  }
}
