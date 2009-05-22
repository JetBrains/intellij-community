package org.jetbrains.idea.maven.project.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.ShowSettingsUtil;
import org.jetbrains.idea.maven.project.MavenIgnoredFilesConfigurable;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.project.ProjectBundle;
import org.jetbrains.idea.maven.utils.MavenAction;
import org.jetbrains.idea.maven.utils.MavenDataKeys;

import java.util.List;

public class ToggleIgnoredProjectsAction extends MavenAction {
  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    if (!isAvailable(e)) return;

    MavenProjectsManager projectsManager = getProjectsManager(e);
    List<MavenProject> projects = e.getData(MavenDataKeys.MAVEN_PROJECTS);

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

    MavenProjectsManager projectsManager = getProjectsManager(e);
    List<MavenProject> projects = e.getData(MavenDataKeys.MAVEN_PROJECTS);

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
    MavenProjectsManager projectsManager = getProjectsManager(e);
    List<MavenProject> projects = e.getData(MavenDataKeys.MAVEN_PROJECTS);

    if (isIgnoredInSettings(projectsManager, projects)) {
      ShowSettingsUtil.getInstance().editConfigurable(getProject(e), new MavenIgnoredFilesConfigurable(projectsManager));
    }
    else {
      projectsManager.setIgnoredState(projects, !isIgnored(projectsManager, projects));
    }
  }
}
