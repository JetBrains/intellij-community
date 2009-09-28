package org.jetbrains.idea.maven.project.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.utils.actions.MavenAction;
import org.jetbrains.idea.maven.utils.actions.MavenActionUtil;

public class RemoveManagedFilesAction extends MavenAction {
  @Override
  protected boolean isAvailable(AnActionEvent e) {
    for (VirtualFile each : MavenActionUtil.getMavenProjectsFiles(e)) {
      if (MavenActionUtil.getProjectsManager(e).isManagedFile(each)) return true;
    }
    return false;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    MavenActionUtil.getProjectsManager(e).removeManagedFiles(MavenActionUtil.getMavenProjectsFiles(e));
  }
}
