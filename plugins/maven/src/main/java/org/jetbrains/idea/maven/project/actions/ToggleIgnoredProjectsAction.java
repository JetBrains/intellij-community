/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.project.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.options.ShowSettingsUtil;
import org.jetbrains.idea.maven.project.MavenIgnoredFilesConfigurable;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.project.ProjectBundle;
import org.jetbrains.idea.maven.utils.actions.MavenAction;
import org.jetbrains.idea.maven.utils.actions.MavenActionUtil;

import java.util.List;

public class ToggleIgnoredProjectsAction extends MavenAction {
  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    if (!isAvailable(e)) return;

    final DataContext context = e.getDataContext();
    MavenProjectsManager projectsManager = MavenActionUtil.getProjectsManager(context);
    List<MavenProject> projects = MavenActionUtil.getMavenProjects(context);

    if (isIgnoredInSettings(projectsManager, projects)) {
      e.getPresentation().setText(ProjectBundle.message("maven.ignore.edit"));
    }
    else if (isIgnored(projectsManager, projects)) {
      e.getPresentation().setText(ProjectBundle.message("maven.unignore"));
    }
    else {
      e.getPresentation().setText(ProjectBundle.message("maven.ignore"));
    }
  }

  @Override
  protected boolean isAvailable(AnActionEvent e) {
    if (!super.isAvailable(e)) return false;

    final DataContext context = e.getDataContext();
    MavenProjectsManager projectsManager = MavenActionUtil.getProjectsManager(context);
    List<MavenProject> projects = MavenActionUtil.getMavenProjects(context);

    if (projects == null || projects.isEmpty()) return false;

    int ignoredStatesCount = 0;
    int ignoredCount = 0;

    for (MavenProject each : projects) {
      if (projectsManager.getIgnoredState(each)) {
        ignoredStatesCount++;
      }
      if (projectsManager.isIgnored(each)) {
        ignoredCount++;
      }
    }

    return (ignoredCount == 0 || ignoredCount == projects.size()) &&
           (ignoredStatesCount == 0 || ignoredStatesCount == projects.size());
  }

  private boolean isIgnored(MavenProjectsManager projectsManager, List<MavenProject> projects) {
    return projectsManager.getIgnoredState(projects.get(0));
  }

  private boolean isIgnoredInSettings(MavenProjectsManager projectsManager, List<MavenProject> projects) {
    return projectsManager.isIgnored(projects.get(0)) && !isIgnored(projectsManager, projects);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final DataContext context = e.getDataContext();
    MavenProjectsManager projectsManager = MavenActionUtil.getProjectsManager(context);
    List<MavenProject> projects = MavenActionUtil.getMavenProjects(context);

    if (isIgnoredInSettings(projectsManager, projects)) {
      ShowSettingsUtil.getInstance().editConfigurable(MavenActionUtil.getProject(context), new MavenIgnoredFilesConfigurable(projectsManager));
    }
    else {
      projectsManager.setIgnoredState(projects, !isIgnored(projectsManager, projects));
    }
  }
}
