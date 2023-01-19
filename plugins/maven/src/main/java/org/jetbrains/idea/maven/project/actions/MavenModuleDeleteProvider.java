// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.actions;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.UnloadedModuleDescription;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.actions.ModuleDeleteProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Ignores maven project if the corresponding module is deleted
 */
public class MavenModuleDeleteProvider extends ModuleDeleteProvider {
  @Override
  protected void doDetachModules(@NotNull Project project,
                                 Module @Nullable [] modules,
                                 @Nullable List<? extends UnloadedModuleDescription> unloadedModules) {
    var mavenProjectsToIgnore = new ArrayList<MavenProject>();
    var projectsManager = MavenProjectsManager.getInstance(project);
    if (null != modules) {
      for (var module : modules) {
        var mavenProject = projectsManager.findProject(module);
        if (null != mavenProject) {
          mavenProjectsToIgnore.add(mavenProject);
        }
      }
    }

    super.doDetachModules(project, modules, unloadedModules);

    projectsManager.setIgnoredState(mavenProjectsToIgnore, true);
  }
}
