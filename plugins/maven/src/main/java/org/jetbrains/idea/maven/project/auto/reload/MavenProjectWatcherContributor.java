// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.auto.reload;

import com.intellij.openapi.externalSystem.service.project.autoimport.ExternalSystemProjectsWatcherImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.buildtool.MavenImportSpec;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

@ApiStatus.Internal
public class MavenProjectWatcherContributor implements ExternalSystemProjectsWatcherImpl.Contributor {

  @Override
  public void markDirtyAllExternalProjects(@NotNull Project project) {
    runWhenFullyOpen(project, (manager) -> manager.scheduleUpdateProjects(List.of(), new MavenImportSpec(true, false, false)));
  }

  @Override
  public void markDirty(@NotNull Module module) {
    runWhenFullyOpen(module.getProject(), (manager) -> {
      MavenProject mavenProject = manager.findProject(module);
      if (mavenProject != null) {
        manager.scheduleUpdateProjects(Collections.singletonList(mavenProject), new MavenImportSpec(true, false, false));
      }
    });
  }

  private static void runWhenFullyOpen(@NotNull Project project, @NotNull Consumer<MavenProjectsManager> consumer) {
    MavenProjectsManager manager = MavenProjectsManager.getInstance(project);
    manager.runWhenFullyOpen(() -> consumer.accept(manager));
  }
}
