package org.jetbrains.idea.maven.project.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.core.MavenDataKeys;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.project.ProjectBundle;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Vladislav.Kaznacheev
 */
public class ToggleProfileAction extends AnAction {

  public void update(final AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    final MavenProjectsManager projectsManager = project != null ? project.getComponent(MavenProjectsManager.class) : null;
    final List<String> profiles = e.getData(MavenDataKeys.MAVEN_PROFILES_KEY);

    final boolean enabled = projectsManager != null && profiles != null && isEnabled(projectsManager, profiles);

    e.getPresentation().setEnabled(enabled);
    e.getPresentation().setText((enabled && projectsManager.getActiveProfiles().contains(profiles.get(0)))
                                ? ProjectBundle.message("maven.profile.deactivate")
                                : ProjectBundle.message("maven.profile.activate"));
  }

  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(PlatformDataKeys.PROJECT);
    MavenProjectsManager manager = project != null ? MavenProjectsManager.getInstance(project) : null;
    VirtualFile file = e.getData(PlatformDataKeys.VIRTUAL_FILE);
    List<String> profiles = e.getData(MavenDataKeys.MAVEN_PROFILES_KEY);

    if (manager != null && profiles != null && isEnabled(manager, profiles)) {
      List<String> activeProfiles = new ArrayList<String>(manager.getActiveProfiles());
      if (activeProfiles.contains(profiles.get(0))) {
        activeProfiles.removeAll(profiles);
      }
      else {
        activeProfiles.addAll(profiles);
      }
      manager.setActiveProfiles(activeProfiles);
    }
  }

  private boolean isEnabled(MavenProjectsManager projectsManager, List<String> profiles) {
    List<String> activeProfiles = projectsManager.getActiveProfiles();
    int count = 0;
    for (String profile : profiles) {
      if (activeProfiles.contains(profile)) {
        count++;
      }
    }
    return count == 0 || count == profiles.size();
  }
}
