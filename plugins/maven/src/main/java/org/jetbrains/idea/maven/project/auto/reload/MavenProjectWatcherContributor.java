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

import java.util.List;

@ApiStatus.Internal
public class MavenProjectWatcherContributor implements ExternalSystemProjectsWatcherImpl.Contributor {

  @Override
  public void markDirtyAllExternalProjects(@NotNull Project project) {
    MavenProjectsManager manager = MavenProjectsManager.getInstance(project);
    manager.scheduleUpdateAllMavenProjects(new MavenImportSpec(true, false, false));
  }

  @Override
  public void markDirty(@NotNull Module module) {
    MavenProjectsManager manager = MavenProjectsManager.getInstance(module.getProject());
    MavenProject mavenProject = manager.findProject(module);
    if (mavenProject != null) {
      manager.scheduleUpdateMavenProjects(new MavenImportSpec(true, false, false), List.of(mavenProject.getFile()), List.of());
    }
  }
}
