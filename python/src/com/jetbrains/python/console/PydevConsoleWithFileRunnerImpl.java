// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.console;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.util.Consumer;
import com.jetbrains.python.run.PythonRunConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class PydevConsoleWithFileRunnerImpl extends PydevConsoleRunnerImpl {
  @NotNull private final PythonRunConfiguration myConfig;

  public PydevConsoleWithFileRunnerImpl(@NotNull Project project,
                                        @NotNull Sdk sdk,
                                        @NotNull PyConsoleType consoleType,
                                        @Nullable String workingDir,
                                        @NotNull Map<String, String> environmentVariables,
                                        @NotNull PyConsoleOptions.PyConsoleSettings settingsProvider,
                                        @NotNull Consumer<String> rerunAction,
                                        @NotNull PythonRunConfiguration config,
                                        String... statementsToExecute) {
    super(project, sdk, consoleType, workingDir, environmentVariables, settingsProvider, rerunAction, statementsToExecute);
    myConfig = config;
  }

  @Override
  protected PythonConsoleRunParams createConsoleRunParams(@Nullable String workingDir,
                                                          @NotNull Sdk sdk,
                                                          @NotNull Map<String, String> environmentVariables) {
    return new PythonConsoleWithFileRunParams(myConsoleSettings, workingDir, sdk, environmentVariables);
  }

  public class PythonConsoleWithFileRunParams extends PythonConsoleRunParams {

    public PythonConsoleWithFileRunParams(@NotNull PyConsoleOptions.PyConsoleSettings consoleSettings,
                                          @Nullable String workingDir,
                                          @NotNull Sdk sdk, @NotNull Map<String, String> envs) {
      super(consoleSettings, workingDir, sdk, envs);
    }

    @Override
    public boolean isUseModuleSdk() {
      return myConfig.isUseModuleSdk();
    }

    @Override
    public String getModuleName() {
      return myConfig.getModuleName();
    }

    @Override
    public String getWorkingDirectory() {
      return super.getWorkingDirectory();
    }

    @Override
    public String getInterpreterOptions() {
      return myConfig.getInterpreterOptions();
    }

    @Override
    public boolean shouldAddContentRoots() {
      return myConfig.shouldAddContentRoots();
    }

    @Override
    public boolean shouldAddSourceRoots() {
      return myConfig.shouldAddSourceRoots();
    }
  }
}
