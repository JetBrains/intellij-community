// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.projectRoot;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ModuleStructureExtension;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.intellij.ide.highlighter.ModuleFileType.DOT_DEFAULT_EXTENSION;
import static org.jetbrains.idea.maven.model.MavenConstants.POM_XML;

public class MavenModuleStructureExtension extends ModuleStructureExtension {
  private final Set<Module> myModulesToRemove = new HashSet<>();
  private final List<String> myPomsToIgnore = new ArrayList<>();
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
        myPomsToIgnore.add(mavenProject.getPath());
      } else {
        // If we add and then remove a maven module in Project Structure,
        // its content stays on the disk and the module will be recreated during the next import.
        // To prevent it, we try to add its pom to ignore list
        var imlPath = moduleToRemove.getModuleFilePath();
        if (imlPath.endsWith(DOT_DEFAULT_EXTENSION)) {
          var pomPath = imlPath.substring(0, imlPath.length() - DOT_DEFAULT_EXTENSION.length()) + "/" + POM_XML;
          if (Files.exists(Path.of(pomPath))) {
            myPomsToIgnore.add(pomPath);
          }
        }
      }
    });
    myModulesToRemove.clear();
  }

  @Override
  public void disposeUIResources() {
    if (null != myMavenProjectsManager) {
      myMavenProjectsManager.setIgnoredStateForPoms(myPomsToIgnore, true);
    }

    myModulesToRemove.clear();
    myPomsToIgnore.clear();
    myMavenProjectsManager = null;
  }
}
