package org.jetbrains.plugins.ruby.ruby.actions.execution;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.GenericProgramRunner;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.ide.actions.runAnything.execution.RunAnythingRunProfile;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Pair;
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
import org.jetbrains.plugins.ruby.ruby.debugger.RubyDebugMode;
import org.jetbrains.plugins.ruby.ruby.debugger.RubyDebugRunner;
import org.jetbrains.plugins.ruby.ruby.debugger.RubyProcessDispatcher;
import org.jetbrains.plugins.ruby.ruby.debugger.SourcePositionConverter;
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

import java.util.Map;

import static org.jetbrains.plugins.ruby.ruby.run.configuration.RubyCommandLineData.IDE_PROCESS_DISPATCHER;
import static org.jetbrains.plugins.ruby.ruby.run.configuration.RubyCommandLineData.RUBYMINE_DEBUG_PORT;

public class RunAnythingDebugRunner extends GenericProgramRunner {
  public static final String ID = "RunAnythingDebugRunner";
  private static final Logger LOG = Logger.getInstance(RunAnythingDebugRunner.class);
  private static final String DEBUGGER_CLI_DEBUG = "DEBUGGER_CLI_DEBUG";

  @NotNull
  public String getRunnerId() {
    return ID;
  }

  public boolean canRun(@NotNull final String executorId, @NotNull final RunProfile profile) {
    return DefaultDebugExecutor.EXECUTOR_ID.equals(executorId) && profile instanceof RunAnythingRunProfile;
  }

  @Nullable
  @Override
  protected RunContentDescriptor doExecute(@NotNull RunProfileState state, @NotNull ExecutionEnvironment environment)
    throws ExecutionException {
    FileDocumentManager.getInstance().saveAllDocuments();

    LOG.debug("Initializing debugger service");

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

    if (!debugGemHelper.isUsable()) {
      throw new ExecutionException(RBundle.message("ruby.debugger.cannot.start.debug", debugGemHelper.getErrorMsg()));
    }

    if (debugGemHelper.needsDebugPreLoader()) {
      applyDebugStarterToEnv(((RunAnythingRunProfile)environment.getRunProfile()).getCommandLine(),
                             rubyRunner,
                             debuggerPort,
                             dispatcher,
                             sdk,
                             debugGemHelper);
    }

    int timeout = RubyDebuggerSettings.getInstance().getState().getTimeout();

    boolean supportsNonSuspendedFramesReading = RubyDebugRunner.supportsNonSuspendedFramesReading(debugGemHelper, sdk);
    boolean supportsCatchpointRemoval = debugGemHelper.supportsCatchpointRemoval();
    final String localHostString = NetUtils.getLocalHostString();
    ExecutionResult executionResult = state.execute(environment.getExecutor(), environment.getRunner());
    if (executionResult == null) {
      throw new ExecutionException("Unable to start process");
    }
    final ProcessHandler serverProcessHandler = executionResult.getProcessHandler();

    final RubyDebuggerProxy rubyDebuggerProxy =
      new RubyDebuggerProxy(timeout, supportsNonSuspendedFramesReading, false, supportsCatchpointRemoval);

    final RubyProcessDispatcher acceptor = RubyDebugRunner
      .getAcceptor(supportsNonSuspendedFramesReading, localHostString, supportsCatchpointRemoval, Integer.valueOf(dispatcher), true);

    rubyDebuggerProxy.setDebugTarget(
      RubyDebugRunner.getDebugTarget(null, serverProcessHandler, debuggerPort, rubyDebuggerProxy, localHostString));

    boolean enableFileFiltering = debugGemHelper.supportFileFiltering()
                                  && RubyDebuggerSettings.getInstance().getState().isStepIntoProjectOnly();
    final SourcePositionConverter converter = new LocalPositionConverter(project, !RubySdkUtil.isRuby18(sdk));

    final XDebugProcessStarter processStarter = new XDebugProcessStarter() {
      @NotNull
      public XDebugProcess start(@NotNull final XDebugSession session) {
        return new RubyDebugProcess(session, state, serverProcessHandler, rubyDebuggerProxy, timeout, converter,
                                    environment.getExecutor(), acceptor, debugGemHelper.pauseActionSupported(),
                                    false,
                                    enableFileFiltering, debugGemHelper.getSourceRoots(project),
                                    debugGemHelper.getExcludedDirs(project)) {
          @NotNull
          @Override
          public ExecutionConsole createConsole() {
            return executionResult.getExecutionConsole();
          }
        };
      }
    };

    XDebugSession debugSession = XDebuggerManager.getInstance(project).startSession(environment, processStarter);

    LOG.debug("Debugger service initialized. Starting RubyDebugProcess");

    return debugSession.getRunContentDescriptor();
  }

  private static void applyDebugStarterToEnv(@NotNull GeneralCommandLine commandLine,
                                             @NotNull RubyLocalRunner rubyRunner,
                                             int debuggerPort,
                                             int dispatcherPort,
                                             @NotNull Sdk sdk,
                                             @NotNull DebugGemHelper debugGemHelper) {
    final Map<String, String> env = commandLine.getEnvironment();
    final String gemsPath = RubyUtil.getScriptFullPath("rb/gems");
    assert gemsPath != null;

    final RubySdkAdditionalData data = RubySdkUtil.getRubySdkAdditionalData(sdk);
    PathMappingSettings mappingSettings = rubyRunner.addDefaultMappings(null);

    String rubyLib = OSUtil.prependToRUBYLIBEnvVariable(data.getSdkSystemAccessor(),
                                                        env.get(RakeRunnerConstants.RUBYLIB_ENVIRONMENT_VARIABLE),
                                                        mappingSettings.convertToRemote(gemsPath));

    env.put(RUBYMINE_DEBUG_PORT, String.valueOf(debuggerPort));
    env.put(DEBUGGER_CLI_DEBUG, String.valueOf(RubyDebuggerSettings.getInstance().getState().isVerboseOutput()));
    env.put(IDE_PROCESS_DISPATCHER, String.valueOf(dispatcherPort));
    env.put(
      RakeRunnerConstants.RUBYLIB_ENVIRONMENT_VARIABLE,
      debugGemHelper.updateRubyLib(rubyLib, data.getSdkSystemAccessor(), mappingSettings)
    );

    OSUtil.appendToEnvVariable(RubyUtil.RUBYOPT, "-rrubymine_debug_anything.rb", env, " ");
  }

}
