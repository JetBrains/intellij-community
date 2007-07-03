package org.jetbrains.idea.maven.state.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.core.MavenDataKeys;
import org.jetbrains.idea.maven.state.MavenProjectsState;
import org.jetbrains.idea.maven.state.StateBundle;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Vladislav.Kaznacheev
 */
public class ToggleProfileAction extends AnAction {

  public void update(final AnActionEvent e) {
    final Project project = e.getData(DataKeys.PROJECT);
    final MavenProjectsState projectsState = project != null ? project.getComponent(MavenProjectsState.class) : null;
    final VirtualFile file = e.getData(DataKeys.VIRTUAL_FILE);
    final List<String> profiles = e.getData(MavenDataKeys.MAVEN_PROFILES_KEY);

    final boolean enabled = projectsState != null && file != null && profiles != null && isEnabled(projectsState, file, profiles);

    e.getPresentation().setEnabled(enabled);
    e.getPresentation().setText((enabled && projectsState.getProfiles(file).contains(profiles.get(0)))
                                ? StateBundle.message("maven.profile.deactivate")
                                : StateBundle.message("maven.profile.activate"));
  }

  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(DataKeys.PROJECT);
    final MavenProjectsState projectsState = project != null ? project.getComponent(MavenProjectsState.class) : null;
    final VirtualFile file = e.getData(DataKeys.VIRTUAL_FILE);
    final List<String> profiles = e.getData(MavenDataKeys.MAVEN_PROFILES_KEY);

    if (projectsState != null && file != null && profiles != null && isEnabled(projectsState, file, profiles)) {
      final Collection<String> activeProfiles = new ArrayList<String>(projectsState.getProfiles(file));
      if (activeProfiles.contains(profiles.get(0))) {
        activeProfiles.removeAll(profiles);
      }
      else {
        activeProfiles.addAll(profiles);
      }
      projectsState.setProfiles(file, activeProfiles);
    }
  }

  private boolean isEnabled(final MavenProjectsState projectsState, final VirtualFile file, final List<String> profiles) {
    final Collection<String> activeProfiles = new ArrayList<String>(projectsState.getProfiles(file));
    int count = 0;
    for (String profile : profiles) {
      if (activeProfiles.contains(profile)) {
        count++;
      }
    }
    return count == 0 || count == profiles.size();
  }
}
