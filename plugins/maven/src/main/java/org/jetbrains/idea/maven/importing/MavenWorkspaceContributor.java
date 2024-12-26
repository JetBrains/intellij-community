// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing;

import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager;
import com.intellij.openapi.externalSystem.model.project.ProjectCoordinate;
import com.intellij.openapi.externalSystem.model.project.ProjectId;
import com.intellij.openapi.externalSystem.service.project.ExternalSystemWorkspaceContributor;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

/**
 * @author Vladislav.Soroka
 */
public final class MavenWorkspaceContributor implements ExternalSystemWorkspaceContributor {
  @Override
  public @Nullable ProjectCoordinate findProjectId(Module module) {
    if (!ExternalSystemModulePropertyManager.getInstance(module).isMavenized()) {
      return null;
    }
    MavenProject mavenProject = MavenProjectsManager.getInstance(module.getProject()).findProject(module);
    if (mavenProject != null) {
      MavenId mavenId = mavenProject.getMavenId();
      return new ProjectId(mavenId.getGroupId(), mavenId.getArtifactId(), mavenId.getVersion());
    }
    return null;
  }
}
