package com.jetbrains.performancePlugin.commands;

import com.intellij.execution.*;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.ui.playback.commands.AbstractCommand;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Ref;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.jetbrains.performancePlugin.PerformanceTestSpan;
import com.jetbrains.performancePlugin.Timer;
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper;
import com.sampullara.cli.Args;
import com.sampullara.cli.Argument;
import io.opentelemetry.api.trace.Span;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import java.util.Arrays;

/**
 * Command runs specified configuration.
 * If configuration is absent prints all available for the project.
 * There are two modes: TILL_STARTED and TILL_TERMINATED
 * Set -failureExpected param if failure expected
 * Set -debug param if you want to run configuration in debug mode
 * <p>
 * Syntax: %runConfiguration [mode] <configurationName> [failureExpected] [debug]
 * Example: %runConfiguration -mode=TILL_TERMINATED|-configurationName=My Run Configuration|-failureExpected|-debug
 */
public final class RunConfigurationCommand extends AbstractCommand {
  public static final String PREFIX = CMD_PREFIX + "runConfiguration";
  private static final String MAIN_SPAN_NAME = "runRunConfiguration";
  private static final String DURATION_SPAN_NAME = "runConfiguration#ProcessDuration";
  @SuppressWarnings("TestOnlyProblems") private ExecutionEnvironment myExecutionEnvironment = new ExecutionEnvironment();

  private static final Logger LOG = Logger.getInstance(RunConfigurationCommand.class);

  public RunConfigurationCommand(@NotNull String text, int line) {
    super(text, line);
  }

  @NotNull
  @Override
  protected Promise<Object> _execute(@NotNull final PlaybackContext context) {
    //example: %runConfiguration -mode=TILL_TERMINATED|-configurationName=My Run Configuration|-failureExpected|-debug
    RunConfigurationOptions options = new RunConfigurationOptions();
    Args.parse(options, Arrays.stream(extractCommandArgument(PREFIX).split("\\|"))
      .flatMap(item -> Arrays.stream(item.split("="))).toArray(String[]::new), false);

    Ref<Span> mainSpan = new Ref<>(), processSpan = new Ref<>();
    Timer timer = new Timer();
    final ActionCallback actionCallback = new ActionCallbackProfilerStopper();

    Project project = context.getProject();

    MessageBusConnection connection = project.getMessageBus().connect();
    connection.subscribe(ExecutionManager.EXECUTION_TOPIC, new ExecutionListener() {
      @Override
      public void processStarting(@NotNull String executorId, @NotNull ExecutionEnvironment env, @NotNull ProcessHandler handler) {
        mainSpan.set(PerformanceTestSpan.TRACER.spanBuilder(MAIN_SPAN_NAME).setParent(PerformanceTestSpan.getContext()).startSpan());
        timer.start();
        myExecutionEnvironment = env;
        context.message("processStarting: " + env, getLine());
      }

      @Override
      public void processStarted(@NotNull String executorId, @NotNull ExecutionEnvironment env, @NotNull ProcessHandler handler) {
        myExecutionEnvironment = env;
        if (options.mode == Mode.TILL_STARTED) {
          mainSpan.get().end();
          timer.stop();
          long executionTime = timer.getTotalTime();
          context.message("processStarted in: " + env + ": " + executionTime, getLine());
          actionCallback.setDone();
        } else {
          processSpan.set(PerformanceTestSpan.TRACER.spanBuilder(DURATION_SPAN_NAME).setParent(
            PerformanceTestSpan.getContext().with(mainSpan.get())
          ).startSpan());
        }
      }

      @Override
      public void processTerminated(@NotNull String executorId,
                                    @NotNull ExecutionEnvironment env,
                                    @NotNull ProcessHandler handler,
                                    int exitCode) {
        if (options.mode == Mode.TILL_TERMINATED) {
          processSpan.get().end();
          mainSpan.get().end();
          timer.stop();
          long executionTime = timer.getTotalTime();
          context.message("processTerminated in: " + env + ": " + executionTime, getLine());
          if (env.equals(myExecutionEnvironment)) {
            if ((exitCode == 0 && !options.failureExpected) || (exitCode != 0 && options.failureExpected)) {
              actionCallback.setDone();
            }
            else {
              actionCallback.reject("Run configuration is finished with exitCode: " + exitCode);
            }
            connection.disconnect();
          }
        }
      }
    });

    RunManagerImpl runManager = RunManagerImpl.getInstanceImpl(project);

    ApplicationManager.getApplication().invokeLater(() -> {
      Executor executor = options.debug ? new DefaultDebugExecutor() : new DefaultRunExecutor();
      ExecutionTarget target = DefaultExecutionTarget.INSTANCE;
      RunConfiguration configurationToRun = getConfigurationByName(runManager, options.configurationName);

      if (configurationToRun == null) {
        actionCallback.reject("Specified configuration is not found: " + options.configurationName);
        printAllConfigurationsNames(runManager);
      }
      else {
        RunnerAndConfigurationSettingsImpl runnerAndConfigurationSettings =
          new RunnerAndConfigurationSettingsImpl(runManager, configurationToRun);

        ExecutionManager.getInstance(project).restartRunProfile(project, executor, target, runnerAndConfigurationSettings, null);
      }
    });

    return Promises.toPromise(actionCallback);
  }

  public static RunConfiguration getConfigurationByName(RunManager runManager, String configurationName) {
    return ContainerUtil.find(runManager.getAllConfigurationsList(), configuration -> configurationName.equals(configuration.getName()));
  }

  private static void printAllConfigurationsNames(RunManager runManager) {
    LOG.info("*****************************");
    LOG.info("Available configurations are:");
    runManager.getAllConfigurationsList().stream().map(RunProfile::getName).forEach(LOG::info);
    LOG.info("*****************************");
  }


  private static class RunConfigurationOptions {
    @Argument
    Mode mode;
    @Argument
    String configurationName;
    @Argument
    boolean failureExpected;
    @Argument
    boolean debug;
  }

  enum Mode {
    TILL_STARTED,
    TILL_TERMINATED
  }
}