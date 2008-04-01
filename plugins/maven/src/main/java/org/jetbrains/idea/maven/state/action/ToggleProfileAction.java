package org.jetbrains.idea.maven.state.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.core.MavenDataKeys;
import org.jetbrains.idea.maven.state.MavenProjectsManager;
import org.jetbrains.idea.maven.state.StateBundle;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Vladislav.Kaznacheev
 */
public class ToggleProfileAction extends AnAction {

  public void update(final AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    final MavenProjectsManager projectsManager = project != null ? project.getComponent(MavenProjectsManager.class) : null;
    final VirtualFile file = e.getData(PlatformDataKeys.VIRTUAL_FILE);
    final List<String> profiles = e.getData(MavenDataKeys.MAVEN_PROFILES_KEY);

    final boolean enabled = projectsManager != null && file != null && profiles != null && isEnabled(projectsManager, file, profiles);

    e.getPresentation().setEnabled(enabled);
    e.getPresentation().setText((enabled && projectsManager.getProfiles(file).contains(profiles.get(0)))
                                ? StateBundle.message("maven.profile.deactivate")
                                : StateBundle.message("maven.profile.activate"));
  }

  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    final MavenProjectsManager projectsManager = project != null ? MavenProjectsManager.getInstance(project) : null;
    final VirtualFile file = e.getData(PlatformDataKeys.VIRTUAL_FILE);
    final List<String> profiles = e.getData(MavenDataKeys.MAVEN_PROFILES_KEY);

    if (projectsManager != null && file != null && profiles != null && isEnabled(projectsManager, file, profiles)) {
      final Collection<String> activeProfiles = new ArrayList<String>(projectsManager.getProfiles(file));
      if (activeProfiles.contains(profiles.get(0))) {
        activeProfiles.removeAll(profiles);
      }
      else {
        activeProfiles.addAll(profiles);
      }
      projectsManager.setProfiles(file, activeProfiles);
    }
  }

  private boolean isEnabled(final MavenProjectsManager projectsManager, final VirtualFile file, final List<String> profiles) {
    final Collection<String> activeProfiles = new ArrayList<String>(projectsManager.getProfiles(file));
    int count = 0;
    for (String profile : profiles) {
      if (activeProfiles.contains(profile)) {
        count++;
      }
    }
    return count == 0 || count == profiles.size();
  }
}
