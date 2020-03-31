// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution.build;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.task.ProjectModelBuildTask;
import com.intellij.task.ProjectTaskContext;
import com.intellij.task.ProjectTaskNotification;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ibessonov
 */
public interface MavenArtifactBuilder {

  ExtensionPointName<MavenArtifactBuilder> EP_NAME =
    ExtensionPointName.create("org.jetbrains.idea.maven.artifactBuilder");

  boolean isApplicable(ProjectModelBuildTask task);

  void build(@NotNull Project project,
             @NotNull ProjectModelBuildTask task,
             @NotNull ProjectTaskContext context,
             @Nullable ProjectTaskNotification callback);
}
