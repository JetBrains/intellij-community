package com.jetbrains.performancePlugin.commands;

import com.intellij.execution.*;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.ui.playback.commands.AbstractCommand;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.util.messages.MessageBusConnection;
import com.jetbrains.performancePlugin.Timer;
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

public final class RunConfigurationCommand extends AbstractCommand {
  public static final String PREFIX = CMD_PREFIX + "runConfiguration";
  private static final String WAIT_FOR_PROCESS_STARTED = "TILL_STARTED";
  private static final String WAIT_FOR_PROCESS_TERMINATED = "TILL_TERMINATED";
  private ExecutionEnvironment myExecutionEnvironment = new ExecutionEnvironment();

  public RunConfigurationCommand(@NotNull String text, int line) {
    super(text, line);
  }

  @NotNull
  @Override
  protected Promise<Object> _execute(@NotNull final PlaybackContext context) {
    //example: %runConfiguration TILL_TERMINATED My Run Configuration
    String[] command = extractCommandArgument(PREFIX).split(" ", 2);
    String mode = command[0];
    String configurationNameToRun = command[1];

    Timer timer = new Timer();
    final ActionCallback actionCallback = new ActionCallbackProfilerStopper();

    Project project = context.getProject();

    MessageBusConnection connection = project.getMessageBus().connect();
    connection.subscribe(ExecutionManager.EXECUTION_TOPIC, new ExecutionListener() {
      @Override
      public void processStarting(@NotNull String executorId, @NotNull ExecutionEnvironment env, @NotNull ProcessHandler handler) {
        timer.start();
        myExecutionEnvironment = env;
        context.message("processStarting: " + env, getLine());
      }

      @Override
      public void processStarted(@NotNull String executorId, @NotNull ExecutionEnvironment env, @NotNull ProcessHandler handler) {
        myExecutionEnvironment = env;
        if (mode.equals(WAIT_FOR_PROCESS_STARTED)) {
          timer.stop();
          long executionTime = timer.getTotalTime();
          context.message("processStarted in: " + env + ": " + executionTime, getLine());
          actionCallback.setDone();
        }
      }

      @Override
      public void processTerminated(@NotNull String executorId,
                                    @NotNull ExecutionEnvironment env,
                                    @NotNull ProcessHandler handler,
                                    int exitCode) {
        if (mode.equals(WAIT_FOR_PROCESS_TERMINATED)) {
          timer.stop();
          long executionTime = timer.getTotalTime();
          context.message("processTerminated in: " + env + ": " + executionTime, getLine());
          if (env.equals(myExecutionEnvironment)) {
            if (exitCode == 0) {
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
      if (!mode.contains(WAIT_FOR_PROCESS_STARTED) && !mode.contains(WAIT_FOR_PROCESS_TERMINATED)) {
        actionCallback.reject("Specified mode is neither TILL_STARTED nor TILL_TERMINATED");
      }

      Executor executor = new DefaultRunExecutor();
      ExecutionTarget target = DefaultExecutionTarget.INSTANCE;
      RunConfiguration configurationToRun = getConfigurationByName(runManager, configurationNameToRun);

      if (configurationToRun == null) {
        actionCallback.reject("Specified configuration is not found: " + configurationNameToRun);
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

  private static RunConfiguration getConfigurationByName(RunManager runManager, String configurationName) {
    return runManager.getAllConfigurationsList().stream().filter(configuration -> configurationName.equals(configuration.getName()))
      .findFirst().orElse(null);
  }

  private static void printAllConfigurationsNames(RunManager runManager) {
    System.out.println("*****************************");
    System.out.println("Available configurations are:");
    runManager.getAllConfigurationsList().stream().map(RunProfile::getName).forEach(System.out::println);
    System.out.println("*****************************");
  }
}