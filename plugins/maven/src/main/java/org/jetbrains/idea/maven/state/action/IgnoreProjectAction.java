package org.jetbrains.idea.maven.state.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.state.MavenIgnoreConfigurable;
import org.jetbrains.idea.maven.state.MavenProjectsManager;
import org.jetbrains.idea.maven.state.StateBundle;

public class IgnoreProjectAction extends AnAction {

  public void update(final AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    final MavenProjectsManager projectsManager = project != null ? MavenProjectsManager.getInstance(project) : null;
    final VirtualFile[] files = e.getData(PlatformDataKeys.VIRTUAL_FILE_ARRAY);

    final boolean enabled = projectsManager != null && files != null && isEnabled(projectsManager, files);
    e.getPresentation().setEnabled(enabled);
    e.getPresentation().setText((enabled && projectsManager.getIgnoredFlag(files[0]))
                                ? StateBundle.message("maven.ignore.clear")
                                : (enabled && projectsManager.isIgnored(files[0]))
                                  ? StateBundle.message("maven.ignore.edit")
                                  : StateBundle.message("maven.ignore"));
  }

  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    final MavenProjectsManager projectsManager = project != null ? MavenProjectsManager.getInstance(project) : null;
    final VirtualFile[] files = e.getData(PlatformDataKeys.VIRTUAL_FILE_ARRAY);

    if (projectsManager != null && files != null && isEnabled(projectsManager, files)) {
      final boolean flag = projectsManager.getIgnoredFlag(files[0]);
      if (flag == projectsManager.isIgnored(files[0])) {
        for (VirtualFile file : files) {
          projectsManager.setIgnoredFlag(file, !flag);
        }
      }
      else {
        ShowSettingsUtil.getInstance().editConfigurable(project, new MavenIgnoreConfigurable(projectsManager));
      }
    }
  }

  private boolean isEnabled(final MavenProjectsManager projectsManager, final VirtualFile[] files) {
    int ignoredCount = 0;
    int individuallyIgnoredCount = 0;

    for (VirtualFile file : files) {
      if (projectsManager.isIgnored(file)) {
        ignoredCount++;
      }
      if (projectsManager.getIgnoredFlag(file)) {
        individuallyIgnoredCount++;
      }
    }

    return (ignoredCount == 0 || ignoredCount == files.length) &&
           (individuallyIgnoredCount == 0 || individuallyIgnoredCount == files.length);
  }
}