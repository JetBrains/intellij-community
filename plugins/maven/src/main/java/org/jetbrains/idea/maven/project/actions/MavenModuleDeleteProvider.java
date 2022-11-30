// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.actions;

import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.UnloadedModuleDescription;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ui.configuration.actions.ModuleDeleteProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Ignores maven project if the corresponding module is deleted
 */
public class MavenModuleDeleteProvider extends ModuleDeleteProvider {
  @Override
  protected void doDetachModules(@NotNull Project project,
                                 Module @Nullable [] modules,
                                 @Nullable List<? extends UnloadedModuleDescription> unloadedModules) {
    MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(project);
    List<MavenProject> mavenProjects = new ArrayList<>();
    if (null != modules) {
      for (Module module : modules) {
        MavenProject mavenProject = projectsManager.findProject(module);
        mavenProjects.add(mavenProject);
      }
    }

    super.doDetachModules(project, modules, unloadedModules);

    for (MavenProject mavenProject : mavenProjects) {
      projectsManager.ignoreMavenProject(mavenProject);
    }
  }

  @Override
  protected void doRemoveModule(@NotNull final Module moduleToRemove,
                                @NotNull Collection<? extends ModifiableRootModel> otherModuleRootModels,
                                @NotNull final ModifiableModuleModel moduleModel) {

    MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(moduleToRemove.getProject());
    MavenProject mavenProject = projectsManager.findProject(moduleToRemove);
    super.doRemoveModule(moduleToRemove, otherModuleRootModels, moduleModel);
    projectsManager.ignoreMavenProject(mavenProject);
  }
}
