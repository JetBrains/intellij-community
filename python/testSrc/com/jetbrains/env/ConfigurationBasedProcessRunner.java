// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.env;

import com.intellij.execution.*;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.runners.DefaultProgramRunnerKt;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.jetbrains.python.run.AbstractPythonRunConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * Runner to run python configurations. You only need to provide {@link PyConfigurationProducerForRunner} (see it's inheritors for useful defaults)
 * Some methods are overridible to allow customization.
 * <p/>
 * This class allows configuration to <strong>rerun</strong> (using {@link #shouldRunAgain()},
 * you need to implement {@link #getEnvironmentToRerun(RunContentDescriptor)}, accept last run descriptor and return
 * {@link ExecutionEnvironment} to rerun (probably obtained from descriptor).
 * <p/>
 * It also has {@link #getAvailableRunnersForLastRun()} with list of runners that represents runner ids available for last run.
 *
 * @param <CONF_T> configuration class this runner supports
 * @author Ilya.Kazakevich
 */
public abstract class ConfigurationBasedProcessRunner<CONF_T extends AbstractPythonRunConfiguration<?>>
  extends ProcessWithConsoleRunner {
  @NotNull
  private final Class<CONF_T> myExpectedConfigurationType;

  @NotNull
  private final List<ProgramRunner<?>> myAvailableRunnersForLastRun = new ArrayList<>();
  @NotNull
  private final PyConfigurationProducerForRunner<CONF_T> myConfigurationProducer;

  /**
   * Environment to be used to run instead of factory. Used to rerun
   */
  private ExecutionEnvironment myRerunExecutionEnvironment;

  /**
   * Process descriptor of last run
   */
  protected RunContentDescriptor myLastProcessDescriptor;


  protected ConfigurationBasedProcessRunner(
    @NotNull Class<CONF_T> expectedConfigurationType,
    @NotNull final PyConfigurationProducerForRunner<CONF_T> configurationProducer) {
    myExpectedConfigurationType = expectedConfigurationType;
    myConfigurationProducer = configurationProducer;
  }

  @Override
  final void runProcess(@NotNull final String sdkPath,
                        @Nullable final Sdk sdk,
                        @NotNull final Project project,
                        @NotNull final ProcessListener processListener,
                        @NotNull final String tempWorkingPath)
    throws ExecutionException {
    ensureConsoleOk(myConsole);

    // Do not create new environment from factory, if child provided environment to rerun
    final ExecutionEnvironment executionEnvironment =
      // TODO: RENAME
      (myRerunExecutionEnvironment != null ? myRerunExecutionEnvironment : createExecutionEnvironment
        (sdkPath, sdk, project, tempWorkingPath));

    // Engine to be run after process end to post process console
    ProcessListener consolePostprocessor = new ProcessAdapter() {
      @Override
      public void processTerminated(@NotNull final ProcessEvent event) {
        super.processTerminated(event);
        ApplicationManager.getApplication().invokeAndWait(() -> prepareConsoleAfterProcessEnd(), ModalityState.NON_MODAL);
      }
    };

    /// Find all available runners to report them to the test
    myAvailableRunnersForLastRun.clear();
    for (ProgramRunner<?> runner : ProgramRunner.PROGRAM_RUNNER_EP.getExtensions()) {
      for (Executor executor : Executor.EXECUTOR_EXTENSION_NAME.getExtensions()) {
        if (runner.canRun(executor.getId(), executionEnvironment.getRunProfile())) {
          myAvailableRunnersForLastRun.add(runner);
        }
      }
    }
    ExecutionResult executionResult =
      executionEnvironment.getState().execute(executionEnvironment.getExecutor(), executionEnvironment.getRunner());
    ProcessHandler handler = executionResult.getProcessHandler();

    handler.addProcessListener(new ProcessAdapter() {
      @Override
      public void startNotified(@NotNull ProcessEvent event) {
        super.startNotified(event);
      }
    });
    RunContentDescriptor descriptor = DefaultProgramRunnerKt.showRunContent(executionResult, executionEnvironment);
    handler.addProcessListener(processListener, project);
    handler.addProcessListener(consolePostprocessor, project);
    fetchConsoleAndSetToField(descriptor);
    assert myConsole != null : "fetchConsoleAndSetToField did not set console!";
    final var component = myConsole.getComponent(); // Console does not work without of this method
    assert component != null;
    myLastProcessDescriptor = descriptor;
    handler.startNotify();
  }

  /**
   * {@link PyProcessWithConsoleTestTask#createProcessRunner()} should always return new runner.
   * But some buggy code returns runner from prev. rerun with stale project in console.
   * This code checks it.
   */
  private static void ensureConsoleOk(@Nullable final ConsoleViewImpl console) {
    if (console == null) { // Not set yet
      return;
    }
    if (console.getProject().isDisposed()) {
      throw new AssertionError(
        "=== Console is stale. Make sure you did not cache and reuse runner from prev. run. See this method doc for more info === ");
    }
  }

  /**
   * @return descriptor of last run
   */
  @Nullable
  public RunContentDescriptor getLastProcessDescriptor() {
    return myLastProcessDescriptor;
  }

  @NotNull
  private ExecutionEnvironment createExecutionEnvironment(@NotNull final String sdkPath,
                                                          @Nullable final Sdk sdk,
                                                          @NotNull final Project project,
                                                          @NotNull final String workingDir)
    throws ExecutionException {

    CONF_T configuration = myConfigurationProducer.createConfiguration(project, myExpectedConfigurationType);
    RunnerAndConfigurationSettings settings =
      new RunnerAndConfigurationSettingsImpl(RunManagerImpl.getInstanceImpl(project), configuration);
    assert myExpectedConfigurationType.isInstance(configuration) :
      String.format("Expected configuration %s, but got %s", myExpectedConfigurationType, configuration.getClass());

    configuration.setSdkHome(sdkPath);
    configuration.setSdk(sdk);
    configuration.setWorkingDirectory(workingDir);

    try {
      WriteAction.run(() -> {
        configurationCreatedAndWillLaunch(configuration);

        RunManager runManager = RunManager.getInstance(project);
        runManager.addConfiguration(settings);
        runManager.setSelectedConfiguration(settings);
        Assert.assertSame(settings, runManager.getSelectedConfiguration());
      });
    }
    catch (IOException e) {
      throw new ExecutionException(e);
    }


    // Execute
    final Executor executor = DefaultRunExecutor.getRunExecutorInstance();
    return ExecutionEnvironmentBuilder.create(executor, settings).build();
  }

  /**
   * Prepares console to be tested after process end. Always call super if override.
   */
  protected void prepareConsoleAfterProcessEnd() {
    myConsole.flushDeferredText(); // Console may have deferred text, lets flush it
  }

  /**
   * Fetches console from process descriptor and sets it to {@link #myConsole}.
   * If override, always set console!
   *
   * @param descriptor process descriptor
   */
  protected void fetchConsoleAndSetToField(@NotNull final RunContentDescriptor descriptor) {
    myConsole = (ConsoleViewImpl)descriptor.getExecutionConsole();
  }

  /**
   * Called when configuration is created. Used by runners to configure configuration before run.
   * Always call parent when override.
   *
   * @param configuration configuration to configure
   */
  protected void configurationCreatedAndWillLaunch(@NotNull final CONF_T configuration) throws IOException {

  }

  @Override
  protected final boolean shouldRunAgain() {
    final ExecutionEnvironment rerunEnvironment = getEnvironmentToRerun(myLastProcessDescriptor);
    if (rerunEnvironment == null) {
      return false;
    }
    myRerunExecutionEnvironment = rerunEnvironment;
    return true;
  }

  /**
   * Fetches rerun environment from descriptor if any
   *
   * @param lastRunDescriptor descriptor of last process run
   * @return environment to rerun or null if no rerun needed
   */
  @Nullable
  protected ExecutionEnvironment getEnvironmentToRerun(@NotNull final RunContentDescriptor lastRunDescriptor) {
    return null;
  }

  /**
   * @return list of runner ids which are allowed to run against current environment.
   * Use it to check if desired runner (like debugger) exists
   */
  @NotNull
  public final List<ProgramRunner<?>> getAvailableRunnersForLastRun() {
    return Collections.unmodifiableList(myAvailableRunnersForLastRun);
  }
}
