// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.projectRoot;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ModuleStructureExtension;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MavenModuleStructureExtension extends ModuleStructureExtension {
  private final List<Module> myModulesToRemove = new ArrayList<>();
  private final Set<MavenProjectsManager> myMavenProjectsManagers = new HashSet<>();

  @Override
  public void moduleRemoved(final Module module) {
    myModulesToRemove.add(module);
  }

  @Override
  public boolean isModified() {
    return !myModulesToRemove.isEmpty();
  }

  @Override
  public void apply() throws ConfigurationException {
    myModulesToRemove.forEach(moduleToRemove -> {
      MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(moduleToRemove.getProject());
      MavenProject mavenProject = projectsManager.findProject(moduleToRemove);
      projectsManager.scheduleMavenProjectToIgnore(mavenProject);
      myMavenProjectsManagers.add(projectsManager);
    });
    myModulesToRemove.clear();
  }

  @Override
  public void disposeUIResources() {
    myMavenProjectsManagers.forEach(mavenProjectsManager -> mavenProjectsManager.importProjects());
    myMavenProjectsManagers.clear();
    myModulesToRemove.clear();
  }
}
