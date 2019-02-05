// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.util;

import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.model.execution.ExternalTaskExecutionInfo;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.execution.MavenRunnerParameters;
import org.jetbrains.idea.maven.execution.MavenRunnerSettings;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;

public class MavenExternalSystemTaskRunner {

  private Project myProject;

  public MavenExternalSystemTaskRunner(@NotNull Project project) {
    myProject = project;
  }

  public void runMavenTask(@NotNull MavenRunnerParameters params,
                           @Nullable MavenGeneralSettings settings,
                           @Nullable MavenRunnerSettings runnerSettings) {
    ExternalSystemTaskExecutionSettings taskExecutionSettings = createExecutionSettings(params, settings, runnerSettings);
    ExternalSystemUtil.runTask(taskExecutionSettings, DefaultRunExecutor.EXECUTOR_ID, myProject, MavenConstants.SYSTEM_ID);
  }

  private ExternalSystemTaskExecutionSettings createExecutionSettings(@NotNull MavenRunnerParameters params,
                                                                      @Nullable MavenGeneralSettings settings,
                                                                      @Nullable MavenRunnerSettings runnerSettings) {
    ExternalSystemTaskExecutionSettings executionSettings = new ExternalSystemTaskExecutionSettings();
    executionSettings.setExternalProjectPath(params.getWorkingDirPath());
    executionSettings.setTaskNames(params.getGoals());
    if (runnerSettings != null) {
      executionSettings.setVmOptions(runnerSettings.getVmOptions());
    }
    executionSettings.setExternalSystemIdString(MavenConstants.SYSTEM_ID.toString());
    return executionSettings;
  }
}
