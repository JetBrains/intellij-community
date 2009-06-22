package org.jetbrains.idea.maven.project.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.project.ProjectBundle;
import org.jetbrains.idea.maven.utils.actions.MavenAction;
import org.jetbrains.idea.maven.utils.actions.MavenActionUtils;
import org.jetbrains.idea.maven.utils.MavenDataKeys;

import java.util.List;

public class ToggleProfileAction extends MavenAction {
  public void update(AnActionEvent e) {
    super.update(e);
    if (!isAvailable(e)) return;

    MavenProjectsManager projectsManager = MavenActionUtils.getProjectsManager(e);
    List<String> profiles = e.getData(MavenDataKeys.MAVEN_PROFILES);

    e.getPresentation().setText(isActive(projectsManager, profiles)
                                ? ProjectBundle.message("maven.profile.deactivate")
                                : ProjectBundle.message("maven.profile.activate"));
  }

  @Override
  protected boolean isAvailable(AnActionEvent e) {
    if (!super.isAvailable(e)) return false;

    List<String> selectedProfiles = e.getData(MavenDataKeys.MAVEN_PROFILES);
    if (selectedProfiles == null || selectedProfiles.isEmpty()) return false;

    List<String> activeProfiles = MavenActionUtils.getProjectsManager(e).getActiveProfiles();
    int activeCount = 0;
    for (String profile : selectedProfiles) {
      if (activeProfiles.contains(profile)) {
        activeCount++;
      }
    }
    return activeCount == 0 || activeCount == selectedProfiles.size();
  }

  private boolean isActive(MavenProjectsManager projectsManager, List<String> profiles) {
    return projectsManager.getActiveProfiles().contains(profiles.get(0));
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    MavenProjectsManager manager = MavenActionUtils.getProjectsManager(e);
    List<String> selectedProfiles = e.getData(MavenDataKeys.MAVEN_PROFILES);

    List<String> activeProfiles = manager.getActiveProfiles();
    if (isActive(manager, selectedProfiles)) {
      activeProfiles.removeAll(selectedProfiles);
    }
    else {
      activeProfiles.addAll(selectedProfiles);
    }
    manager.setActiveProfiles(activeProfiles);
  }
}
