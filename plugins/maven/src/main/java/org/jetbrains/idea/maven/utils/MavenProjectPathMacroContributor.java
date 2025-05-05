// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.utils;

import com.intellij.application.options.PathMacrosImpl;
import com.intellij.openapi.components.impl.ProjectWidePathMacroContributor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.platform.eel.EelDescriptor;
import com.intellij.platform.eel.provider.EelProviderUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

/**
 * Maven home path depends on an environment where the project is located.
 * On one hand, we have an application-wide macros in `path.macros.xml`. The data from these macros is inapplicable to non-local projects,
 * such as WSL and Docker based. Here we decide the location by project.
 */
final class MavenProjectPathMacroContributor implements ProjectWidePathMacroContributor {

  @Override
  public @NotNull Map<@NotNull String, @NotNull String> getProjectPathMacros(@NotNull @SystemIndependent String projectFilePath) {
    //noinspection deprecation
    return Map.of(PathMacrosImpl.MAVEN_REPOSITORY, getPathToDefaultMavenLocalRepositoryOnSpecificEnv(projectFilePath));
  }

  static @NotNull String getPathToDefaultMavenLocalRepositoryOnSpecificEnv(@NotNull @SystemIndependent String projectFilePath) {
    Path projectFile = Path.of(projectFilePath);

    String serializedPath = getBySerializedProjectPath(projectFilePath);
    if (serializedPath != null) {
      return serializedPath;
    }

    return MavenUtil.resolveDefaultLocalRepository(projectFile).toAbsolutePath().toString();
  }

  private static @Nullable String getBySerializedProjectPath(@NotNull String projectFilePath){

    ProjectManager projectManager = ProjectManager.getInstanceIfCreated();
    if (projectManager == null) {
      return null;
    }
    Project[] projects = projectManager.getOpenProjects();
    for (Project project : projects) {
      if (projectFilePath.equals(project.getProjectFilePath())) {
        MavenSerializedRepositoryManager manager = project.getService(MavenSerializedRepositoryManager.class);
        if (manager.isOverriden()) return manager.getMavenHomePath().toString();
      }
    }
    return null;
  }
}
