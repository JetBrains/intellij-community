package org.jetbrains.idea.maven.project.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import org.jetbrains.idea.maven.core.MavenDataKeys;
import org.jetbrains.idea.maven.project.MavenProjectModel;
import org.jetbrains.idea.maven.project.ProjectBundle;
import org.jetbrains.idea.maven.project.MavenIgnoreConfigurable;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import java.util.List;

public class IgnoreProjectAction extends AnAction {

  public void update(final AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    final MavenProjectsManager projectsManager = project != null ? MavenProjectsManager.getInstance(project) : null;
    final List<MavenProjectModel> nodes = e.getData(MavenDataKeys.MAVEN_PROJECT_NODES);

    final boolean enabled = projectsManager != null && nodes != null && isEnabled(projectsManager, nodes);
    e.getPresentation().setEnabled(enabled);
    e.getPresentation().setText((enabled && projectsManager.getIgnoredFlag(nodes.get(0)))
                                ? ProjectBundle.message("maven.ignore.clear")
                                : (enabled && projectsManager.isIgnored(nodes.get(0)))
                                  ? ProjectBundle.message("maven.ignore.edit")
                                  : ProjectBundle.message("maven.ignore"));
  }

  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    final MavenProjectsManager projectsManager = project != null ? MavenProjectsManager.getInstance(project) : null;
    final List<MavenProjectModel> nodes = e.getData(MavenDataKeys.MAVEN_PROJECT_NODES);

    if (projectsManager != null && nodes != null && isEnabled(projectsManager, nodes)) {
      final boolean flag = projectsManager.getIgnoredFlag(nodes.get(0));
      if (flag == projectsManager.isIgnored(nodes.get(0))) {
        for (MavenProjectModel each : nodes) {
          projectsManager.setIgnoredFlag(each, !flag);
        }
      }
      else {
        ShowSettingsUtil.getInstance().editConfigurable(project, new MavenIgnoreConfigurable(projectsManager));
      }
    }
  }

  private boolean isEnabled(final MavenProjectsManager projectsManager, final List<MavenProjectModel> nodes) {
    int ignoredCount = 0;
    int individuallyIgnoredCount = 0;

    for (MavenProjectModel each : nodes) {
      if (projectsManager.isIgnored(each)) {
        ignoredCount++;
      }
      if (projectsManager.getIgnoredFlag(each)) {
        individuallyIgnoredCount++;
      }
    }

    return (ignoredCount == 0 || ignoredCount == nodes.size()) &&
           (individuallyIgnoredCount == 0 || individuallyIgnoredCount == nodes.size());
  }
}