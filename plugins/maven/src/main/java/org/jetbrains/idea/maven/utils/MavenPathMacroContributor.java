// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.utils;

import com.intellij.application.options.PathMacrosImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathMacroContributor;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.components.impl.ProjectWidePathMacroContributor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.platform.eel.EelDescriptor;
import com.intellij.platform.eel.provider.EelProviderUtil;
import com.intellij.platform.eel.provider.LocalEelDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Maven home path depends on an environment where the project is located.
 * On one hand, we have an application-wide macros in `path.macros.xml`. The data from these macros is inapplicable to non-local projects,
 * such as WSL and Docker based. Here we decide the location by project.
 */
final class MavenPathMacroContributor implements ProjectWidePathMacroContributor, PathMacroContributor {

  @Override
  public void registerPathMacros(@NotNull Map<String, String> macros, @NotNull Map<String, String> legacyMacros) {
    String repository = MavenUtil.resolveDefaultLocalRepository(null).toAbsolutePath().toString();
    macros.put(PathMacrosImpl.MAVEN_REPOSITORY, repository);
  }

  @Override
  public void forceRegisterPathMacros(@NotNull Map<String, String> macros) {
    if (System.getProperty(MavenUtil.MAVEN_REPO_LOCAL) != null) {
      macros.put(PathMacrosImpl.MAVEN_REPOSITORY, System.getProperty(MavenUtil.MAVEN_REPO_LOCAL));
    }
  }

  @Override
  public @NotNull Map<@NotNull String, @NotNull String> getProjectPathMacros(@NotNull @SystemIndependent String projectFilePath) {
    return Map.of(PathMacrosImpl.MAVEN_REPOSITORY, getPathToMavenHome(projectFilePath));
  }

  static @NotNull String getPathToMavenHome(@NotNull @SystemIndependent String projectFilePath) {
    Path projectFile = Path.of(projectFilePath);
    EelDescriptor descriptor = EelProviderUtil.getEelDescriptor(projectFile);

    String serializedPath = getBySerializedProjectPath(descriptor, projectFilePath, projectFile);
    if (serializedPath != null) {
      return serializedPath;
    }

    return MavenUtil.resolveDefaultLocalRepository(projectFile).toAbsolutePath().toString();
  }

  private static @Nullable String getBySerializedProjectPath(@NotNull EelDescriptor descriptor,
                                                             @NotNull String projectFilePath,
                                                             Path projectFile) {
    if (descriptor.equals(LocalEelDescriptor.INSTANCE) &&
        !Files.exists(projectFile.getParent().resolve(MavenSerializedRepositoryManager.MAVEN_REPOSITORY_MANAGER_STORAGE))) {
      // we use the globally defined path macro only if the project has not overridden the setting of its maven home
      return PathMacroManager.getInstance(ApplicationManager.getApplication()).expandPath("$" + PathMacrosImpl.MAVEN_REPOSITORY + "$");
    }
    ProjectManager projectManager = ProjectManager.getInstanceIfCreated();
    if (projectManager == null) {
      return null;
    }
    Project[] projects = projectManager.getOpenProjects();
    for (Project project : projects) {
      if (projectFilePath.equals(project.getProjectFilePath())) {
        return project.getService(MavenSerializedRepositoryManager.class).getMavenHomePath().toString();
      }
    }
    return null;
  }
}
