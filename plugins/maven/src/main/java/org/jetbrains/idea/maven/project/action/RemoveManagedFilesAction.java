package org.jetbrains.idea.maven.project.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.utils.MavenAction;

import java.util.Arrays;

public class RemoveManagedFilesAction extends MavenAction {
  @Override
  protected boolean isAvailable(AnActionEvent e) {
    VirtualFile[] files = e.getData(PlatformDataKeys.VIRTUAL_FILE_ARRAY);

    if (files == null || files.length == 0) return false;
    for (VirtualFile each : files) {
      if (getProjectsManager(e).isManagedFile(each)) return true;
    }
    return false;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    VirtualFile[] files = e.getData(PlatformDataKeys.VIRTUAL_FILE_ARRAY);
    getProjectsManager(e).removeManagedFiles(Arrays.asList(files));
  }
}
