package org.jetbrains.idea.maven.project.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.utils.actions.MavenAction;
import org.jetbrains.idea.maven.utils.actions.MavenActionUtil;

import java.util.List;

public abstract class MavenOpenFilesAction extends MavenAction {
  @Override
  protected boolean isAvailable(AnActionEvent e) {
    return super.isAvailable(e) && !getFiles(e).isEmpty();
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    for (VirtualFile each : getFiles(e)) {
      new OpenFileDescriptor(MavenActionUtil.getProject(e), each).navigate(true);
    }
  }

  protected abstract List<VirtualFile> getFiles(AnActionEvent e);
}
