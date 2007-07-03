package org.jetbrains.idea.maven.state.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.state.MavenProjectsState;
import org.jetbrains.idea.maven.state.StateBundle;

public class IgnoreProjectAction extends AnAction {

  public void update(final AnActionEvent e) {
    final Project project = e.getData(DataKeys.PROJECT);
    final MavenProjectsState projectsState = project != null ? project.getComponent(MavenProjectsState.class) : null;
    final VirtualFile[] files = e.getData(DataKeys.VIRTUAL_FILE_ARRAY);

    final boolean enabled = projectsState != null && files != null && isEnabled(projectsState, files);
    e.getPresentation().setEnabled(enabled);
    e.getPresentation().setText((enabled && projectsState.getIgnoredFlag(files[0]))
                                ? StateBundle.message("maven.ignore.clear")
                                : (enabled && projectsState.isIgnored(files[0]))
                                  ? StateBundle.message("maven.ignore.edit")
                                  : StateBundle.message("maven.ignore"));
  }

  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(DataKeys.PROJECT);
    final MavenProjectsState projectsState = project != null ? project.getComponent(MavenProjectsState.class) : null;
    final VirtualFile[] files = e.getData(DataKeys.VIRTUAL_FILE_ARRAY);

    if (projectsState != null && files != null && isEnabled(projectsState, files)) {
      final boolean flag = projectsState.getIgnoredFlag(files[0]);
      if (flag == projectsState.isIgnored(files[0])) {
        for (VirtualFile file : files) {
          projectsState.setIgnoredFlag(file, !flag);
        }
      }
      else {
        ShowSettingsUtil.getInstance().editConfigurable(project, new MavenIgnoreConfigurable(projectsState));
      }
    }
  }

  private boolean isEnabled(final MavenProjectsState projectsState, final VirtualFile[] files) {
    int ignoredCount = 0;
    int individuallyIgnoredCount = 0;

    for (VirtualFile file : files) {
      if (projectsState.isIgnored(file)) {
        ignoredCount++;
      }
      if (projectsState.getIgnoredFlag(file)) {
        individuallyIgnoredCount++;
      }
    }

    return (ignoredCount == 0 || ignoredCount == files.length) &&
           (individuallyIgnoredCount == 0 || individuallyIgnoredCount == files.length);
  }
}