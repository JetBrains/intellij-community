package org.jetbrains.idea.maven.project.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.utils.MavenAction;

public class RemoveManagedFilesAction extends MavenAction {
  @Override
  protected boolean isAvailable(AnActionEvent e) {
    for (VirtualFile each : getFiles(e)) {
      if (getProjectsManager(e).isManagedFile(each)) return true;
    }
    return false;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    getProjectsManager(e).removeManagedFiles(getFiles(e));
  }
}
