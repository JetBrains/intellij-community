package org.jetbrains.idea.maven.project.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.utils.actions.MavenAction;
import org.jetbrains.idea.maven.utils.actions.MavenActionUtils;

public class RemoveManagedFilesAction extends MavenAction {
  @Override
  protected boolean isAvailable(AnActionEvent e) {
    for (VirtualFile each : MavenActionUtils.getFiles(e)) {
      if (MavenActionUtils.getProjectsManager(e).isManagedFile(each)) return true;
    }
    return false;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    MavenActionUtils.getProjectsManager(e).removeManagedFiles(MavenActionUtils.getFiles(e));
  }
}
