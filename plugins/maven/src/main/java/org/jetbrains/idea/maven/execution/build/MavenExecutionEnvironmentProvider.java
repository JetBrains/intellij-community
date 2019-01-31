// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution.build;

import com.intellij.execution.Executor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.task.ExecuteRunConfigurationTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ibessonov
 */
public interface MavenExecutionEnvironmentProvider {

  ExtensionPointName<MavenExecutionEnvironmentProvider> EP_NAME =
    ExtensionPointName.create("org.jetbrains.idea.maven.executionEnvironmentProvider");

  boolean isApplicable(@NotNull ExecuteRunConfigurationTask task);

  @Nullable
  ExecutionEnvironment createExecutionEnvironment(@NotNull Project project, @NotNull ExecuteRunConfigurationTask task,
                                                  @Nullable Executor executor);
}

