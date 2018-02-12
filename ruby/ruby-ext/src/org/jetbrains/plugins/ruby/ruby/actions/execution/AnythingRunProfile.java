package org.jetbrains.plugins.ruby.ruby.actions.execution;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.CommandLineState;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.process.KillableColoredProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PathMappingSettings;
import com.intellij.util.net.NetUtils;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugProcessStarter;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.RBundle;
import org.jetbrains.plugins.ruby.ruby.RModuleUtil;
import org.jetbrains.plugins.ruby.ruby.RubyUtil;
import org.jetbrains.plugins.ruby.ruby.actions.handlers.RunAnythingCommandHandler;
import org.jetbrains.plugins.ruby.ruby.debugger.*;
import org.jetbrains.plugins.ruby.ruby.debugger.impl.LocalPositionConverter;
import org.jetbrains.plugins.ruby.ruby.debugger.impl.RubyDebugProcess;
import org.jetbrains.plugins.ruby.ruby.debugger.settings.RubyDebuggerSettings;
import org.jetbrains.plugins.ruby.ruby.run.PortForwarding;
import org.jetbrains.plugins.ruby.ruby.run.RubyLocalRunner;
import org.jetbrains.plugins.ruby.ruby.run.configuration.DebugGemHelper;
import org.jetbrains.plugins.ruby.ruby.run.configuration.RubyAbstractCommandLineState;
import org.jetbrains.plugins.ruby.ruby.sdk.RubySdkAdditionalData;
import org.jetbrains.plugins.ruby.ruby.sdk.RubySdkUtil;
import org.jetbrains.plugins.ruby.testing.testunit.runConfigurations.RakeRunnerConstants;
import org.jetbrains.plugins.ruby.utils.OSUtil;
import org.rubyforge.debugcommons.RubyDebuggerProxy;

import javax.swing.*;
import java.util.Map;
import java.util.Objects;

import static org.jetbrains.plugins.ruby.ruby.actions.RunAnythingCommandItem.UNDEFINED_COMMAND_ICON;
import static org.jetbrains.plugins.ruby.ruby.actions.RunAnythingUtil.getOrCreateWrappedCommands;

public class AnythingRunProfile implements DebuggableRunProfile {
  @NotNull private final Project myProject;
  @NotNull private final String myOriginalCommand;
  @NotNull private final Executor myExecutor;
  @NotNull private final GeneralCommandLine myCommandLine;

  @Nullable
  private ProcessHandler myProcessHandler;

  public AnythingRunProfile(@NotNull Project project,
                            @NotNull Executor executor,
                            @NotNull GeneralCommandLine commandLine,
                            @NotNull String originalCommand) {
    myExecutor = executor;
    myCommandLine = commandLine;
    myProject = project;
    myOriginalCommand = originalCommand;
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
    return myOriginalCommand;
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
        print(RBundle.message("run.anything.console.process.finished", exitCode), ConsoleViewContentType.SYSTEM_OUTPUT);
        super.notifyProcessTerminated(exitCode);
      }

      @Override
      protected void destroyProcessImpl() {
        super.destroyProcessImpl();
        if (!isProcessTerminated()) {
          print("exit\n", ConsoleViewContentType.USER_INPUT);
        }
      }

      @Override
      public boolean isSilentlyDestroyOnClose() {
        try {
          return RunAnythingCommandHandler.isSilentlyDestroyOnClose(myOriginalCommand);
        }
        catch (RuntimeException e) {
          return super.isSilentlyDestroyOnClose();
        }
      }

      @Override
      public final boolean shouldKillProcessSoftly() {
        try {
          return RunAnythingCommandHandler.shouldKillProcessSoftly(myOriginalCommand);
        }
        catch (RuntimeException e) {
          return super.shouldKillProcessSoftly();
        }
      }
    };

    myProcessHandler.addProcessListener(new ProcessAdapter() {
      boolean myIsFirstLineAdded;

      @Override
      public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
        if (!myIsFirstLineAdded) {
          Objects.requireNonNull(getOrCreateWrappedCommands(myProject))
                 .add(Pair.create(StringUtil.trim(event.getText()), myOriginalCommand));
          myIsFirstLineAdded = true;
        }
      }
    });
    ((KillableColoredProcessHandler)myProcessHandler).setHasPty(true);
    return myProcessHandler;
  }

  private void print(@NotNull String message, @NotNull ConsoleViewContentType consoleViewContentType) {
    ConsoleView console = getConsoleView();
    if (console != null) console.print(message, consoleViewContentType);
  }

  @Nullable
  private ConsoleView getConsoleView() {
    RunContentDescriptor contentDescriptor =
      ExecutionManager.getInstance(myProject).getContentManager().findContentDescriptor(myExecutor, myProcessHandler);

    ConsoleView console = null;
    if (contentDescriptor != null && contentDescriptor.getExecutionConsole() instanceof ConsoleView) {
      console = (ConsoleView)contentDescriptor.getExecutionConsole();
    }
    return console;
  }
}
