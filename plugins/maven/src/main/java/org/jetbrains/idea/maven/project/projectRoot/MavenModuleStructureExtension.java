// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.projectRoot;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ModuleStructureExtension;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import java.util.HashSet;
import java.util.Set;

public class MavenModuleStructureExtension extends ModuleStructureExtension {
  private final Set<Module> myModulesToRemove = new HashSet<>();
  private final Set<MavenProject> myMavenProjectsToIgnore = new HashSet<>();
  private MavenProjectsManager myMavenProjectsManager = null;

  @Override
  public void moduleRemoved(final Module module) {
    myModulesToRemove.add(module);
    if (null == myMavenProjectsManager) {
      myMavenProjectsManager = MavenProjectsManager.getInstance(module.getProject());
    }
  }

  @Override
  public boolean isModified() {
    return !myModulesToRemove.isEmpty();
  }

  @Override
  public void apply() throws ConfigurationException {
    myModulesToRemove.forEach(moduleToRemove -> {
      var mavenProject = myMavenProjectsManager.findProject(moduleToRemove);
      if (null != mavenProject) {
        myMavenProjectsToIgnore.add(mavenProject);
      }
    });
    myModulesToRemove.clear();
  }

  @Override
  public void disposeUIResources() {
    if (null != myMavenProjectsManager) {
      myMavenProjectsManager.scheduleMavenProjectsToIgnore(myMavenProjectsToIgnore);
    }

    myModulesToRemove.clear();
    myMavenProjectsToIgnore.clear();
    myMavenProjectsManager = null;
  }
}
