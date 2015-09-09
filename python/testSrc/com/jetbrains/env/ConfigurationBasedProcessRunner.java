/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.env;

import com.intellij.execution.*;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.run.AbstractPythonRunConfigurationParams;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;

import javax.swing.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * Runner to run python configurations. You only need to provide factory.
 * Some methods are overridible to allow customization.
 * <p/>
 * This class allows configuration to <strong>rerun</strong> (using {@link #shouldRunAgain()},
 * you need to implement {@link #getEnvironmentToRerun(RunContentDescriptor)}, accept last run descriptor and return
 * {@link ExecutionEnvironment} to rerun (probably obtained from descriptor).
 * <p/>
 * It also has {@link #getAvailableRunnersForLastRun()} with list of strings that represents runner ids available for last run.
 *
 * @param <CONF_T> configuration class this runner supports
 * @author Ilya.Kazakevich
 */
public abstract class ConfigurationBasedProcessRunner<CONF_T extends AbstractPythonRunConfigurationParams>
  extends ProcessWithConsoleRunner {
  @Nullable
  protected final String myWorkingFolder;
  @NotNull
  private final ConfigurationFactory myConfigurationFactory;
  @NotNull
  private final Class<CONF_T> myExpectedConfigurationType;

  @NotNull
  private final List<String> myAvailableRunnersForLastRun = new ArrayList<String>();

  /**
   * Environment to be used to run instead of factory. Used to rerun
   */
  private ExecutionEnvironment myRerunExecutionEnvironment;

  /**
   * Process descriptor of last run
   */
  private RunContentDescriptor myLastProcessDescriptor;

  /**
   * @param configurationFactory      factory tp create configurations
   * @param expectedConfigurationType configuration type class
   * @param workingFolder             folder to pass to configuration: {@link AbstractPythonRunConfigurationParams#setWorkingDirectory(String)}
   */
  protected ConfigurationBasedProcessRunner(@NotNull final ConfigurationFactory configurationFactory,
                                            @NotNull final Class<CONF_T> expectedConfigurationType,
                                            @Nullable final String workingFolder) {
    myConfigurationFactory = configurationFactory;
    myExpectedConfigurationType = expectedConfigurationType;
    myWorkingFolder = workingFolder;
  }

  @Override
  final void runProcess(@NotNull final String sdkPath, @NotNull final Project project, @NotNull final ProcessListener processListener)
    throws ExecutionException {
    // Do not create new environment from factory, if child provided environment to rerun
    final ExecutionEnvironment executionEnvironment =
      (myRerunExecutionEnvironment != null ? myRerunExecutionEnvironment : createExecutionEnvironment(sdkPath, project));

    // Engine to be run after process end to post process console
    final ProcessListener consolePostprocessor = new ProcessAdapter() {
      @Override
      public void processTerminated(final ProcessEvent event) {
        super.processTerminated(event);
        ApplicationManager.getApplication().invokeAndWait(new Runnable() {
          @Override
          public void run() {
            prepareConsoleAfterProcessEnd();
          }
        }, ModalityState.NON_MODAL);
      }
    };


    /// Find all available runners to report them to the test
    myAvailableRunnersForLastRun.clear();
    for (final ProgramRunner<?> runner : ProgramRunner.PROGRAM_RUNNER_EP.getExtensions()) {
      for (final Executor executor : Executor.EXECUTOR_EXTENSION_NAME.getExtensions()) {
        if (runner.canRun(executor.getId(), executionEnvironment.getRunProfile())) {
          myAvailableRunnersForLastRun.add(runner.getRunnerId());
        }
      }
    }

    executionEnvironment.getRunner().execute(executionEnvironment, new ProgramRunner.Callback() {
      @Override
      public void processStarted(final RunContentDescriptor descriptor) {
        final ProcessHandler handler = descriptor.getProcessHandler();
        assert handler != null : "No process handler";
        handler.addProcessListener(consolePostprocessor);
        handler.addProcessListener(processListener);
        myConsole = null;
        fetchConsoleAndSetToField(descriptor);
        assert myConsole != null : "fetchConsoleAndSetToField did not set console!";
        final JComponent component = myConsole.getComponent(); // Console does not work with out of this method
        assert component != null;
        myLastProcessDescriptor = descriptor;
      }
    });
  }

  @NotNull
  private ExecutionEnvironment createExecutionEnvironment(@NotNull String sdkPath, @NotNull final Project project)
    throws ExecutionException {
    final RunnerAndConfigurationSettings settings =
      RunManager.getInstance(project).createRunConfiguration("test", myConfigurationFactory);

    final AbstractPythonRunConfigurationParams config = (AbstractPythonRunConfigurationParams)settings.getConfiguration();

    assert myExpectedConfigurationType.isInstance(config) :
      String.format("Expected configuration %s, but got %s", myExpectedConfigurationType, config.getClass());

    @SuppressWarnings("unchecked") // Checked by assert
    final CONF_T castedConfiguration = (CONF_T)config;
    castedConfiguration.setSdkHome(sdkPath);

    new WriteAction() {
      @Override
      protected void run(@NotNull final Result result) throws Throwable {
        configurationCreatedAndWillLaunch(castedConfiguration);

        RunManagerEx.getInstanceEx(project).addConfiguration(settings, false);
        RunManagerEx.getInstanceEx(project).setSelectedConfiguration(settings);
        Assert.assertSame(settings, RunManagerEx.getInstanceEx(project).getSelectedConfiguration());
      }
    }.execute();


    // Execute
    final Executor executor = DefaultRunExecutor.getRunExecutorInstance();
    return ExecutionEnvironmentBuilder.create(executor, settings).build();
  }

  /**
   * Prepares console to be tested after process end. Always call super if override.
   */
  protected void prepareConsoleAfterProcessEnd() {
    myConsole.flushDeferredText(); // Console may have deffered text, lets flush it
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
    configuration.setWorkingDirectory(myWorkingFolder);
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
  public final List<String> getAvailableRunnersForLastRun() {
    return Collections.unmodifiableList(myAvailableRunnersForLastRun);
  }
}
