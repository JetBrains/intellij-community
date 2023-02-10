// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.navigator.structure;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VfsUtilCore;
import org.jetbrains.idea.maven.execution.MavenRunConfiguration;
import org.jetbrains.idea.maven.execution.MavenRunConfigurationType;
import org.jetbrains.idea.maven.project.MavenProject;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static icons.ExternalSystemIcons.Task;
import static org.jetbrains.idea.maven.project.MavenProjectBundle.message;

class RunConfigurationsNode extends GroupNode {

  private final List<RunConfigurationNode> myChildren = new CopyOnWriteArrayList<>();

  RunConfigurationsNode(MavenProjectsStructure structure, ProjectNode parent) {
    super(structure, parent);
    getTemplatePresentation().setIcon(Task);
  }

  @Override
  public String getName() {
    return message("view.node.run.configurations");
  }

  @Override
  protected List<? extends MavenSimpleNode> doGetChildren() {
    return myChildren;
  }

  public void updateRunConfigurations(MavenProject mavenProject) {
    final var childChanged = new Ref<>(false);

    Set<RunnerAndConfigurationSettings> settings = new HashSet<>(
      RunManager.getInstance(myProject).getConfigurationSettingsList(MavenRunConfigurationType.getInstance()));

    myChildren.forEach(node -> {
      if (settings.remove(node.getSettings())) {
        node.updateRunConfiguration();
      }
      else {
        myChildren.remove(node);
        childChanged.set(true);
      }
    });

    int oldSize = myChildren.size();

    for (RunnerAndConfigurationSettings cfg : settings) {
      MavenRunConfiguration mavenRunConfiguration = (MavenRunConfiguration)cfg.getConfiguration();

      if (VfsUtilCore.pathEqualsTo(mavenProject.getDirectoryFile(), mavenRunConfiguration.getRunnerParameters().getWorkingDirPath())) {
        myChildren.add(new RunConfigurationNode(myMavenProjectsStructure, this, cfg));
      }
    }

    if (oldSize != myChildren.size()) {
      childChanged.set(true);
      sort(myChildren);
    }

    if (childChanged.get()) {
      childrenChanged();
    }
  }
}
