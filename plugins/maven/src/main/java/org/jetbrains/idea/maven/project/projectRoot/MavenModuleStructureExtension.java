// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.projectRoot;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ModuleStructureExtension;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.project.MavenProjectPathHolder;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.intellij.ide.highlighter.ModuleFileType.DOT_DEFAULT_EXTENSION;
import static org.jetbrains.idea.maven.model.MavenConstants.SLASH_POM_XML;

public class MavenModuleStructureExtension extends ModuleStructureExtension {
  private final Set<Module> myModulesToRemove = new HashSet<>();
  private final List<MavenProjectPathHolder> myMavenProjectsToIgnore = new ArrayList<>();
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

  private static class NonExistingMavenProject implements MavenProjectPathHolder {
    private final String path;

    NonExistingMavenProject(String path) {
      this.path = path;
    }

    @NotNull
    @Override
    public @NonNls String getPath() {
      return path;
    }
  }

  @Override
  public void apply() throws ConfigurationException {
    myModulesToRemove.forEach(moduleToRemove -> {
      var mavenProject = myMavenProjectsManager.findProject(moduleToRemove);
      if (null != mavenProject) {
        myMavenProjectsToIgnore.add(mavenProject);
      } else {
        // If we add and then remove a maven module in Project Structure,
        // its content stays on the disk and the module is immediately re-imported.
        // To prevent it, we try to add its pom to ignore list
        var imlPath = moduleToRemove.getModuleFilePath();
        if (imlPath.endsWith(DOT_DEFAULT_EXTENSION)) {
          var pomPath = imlPath.substring(0, imlPath.length() - DOT_DEFAULT_EXTENSION.length()) + SLASH_POM_XML;
          myMavenProjectsToIgnore.add(new NonExistingMavenProject(pomPath));
        }
      }
    });
    myModulesToRemove.clear();
  }

  @Override
  public void disposeUIResources() {
    if (null != myMavenProjectsManager) {
      myMavenProjectsManager.setIgnoredState(myMavenProjectsToIgnore, true);
    }

    myModulesToRemove.clear();
    myMavenProjectsToIgnore.clear();
    myMavenProjectsManager = null;
  }
}
