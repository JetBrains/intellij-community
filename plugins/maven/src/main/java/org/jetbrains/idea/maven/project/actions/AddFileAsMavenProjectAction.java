package org.jetbrains.idea.maven.project.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.actions.MavenAction;
import org.jetbrains.idea.maven.utils.actions.MavenActionUtil;

import java.util.Collections;

public class AddFileAsMavenProjectAction extends MavenAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    MavenProjectsManager manager = MavenActionUtil.getProjectsManager(e);
    manager.addManagedFiles(Collections.singletonList(getSelectedFile(e)));
  }

  @Override
  protected boolean isAvailable(AnActionEvent e) {
    VirtualFile file = getSelectedFile(e);
    return super.isAvailable(e)
           && MavenActionUtil.isMavenProjectFile(file)
           && !isExistingProjectFile(e, file);
  }

  @Override
  protected boolean isVisible(AnActionEvent e) {
    return super.isVisible(e) && isAvailable(e);
  }

  private boolean isExistingProjectFile(AnActionEvent e, VirtualFile file) {
    MavenProjectsManager manager = MavenActionUtil.getProjectsManager(e);
    return manager.findProject(file) != null;
  }

  private VirtualFile getSelectedFile(AnActionEvent e) {
    return e.getData(PlatformDataKeys.VIRTUAL_FILE);
  }
}
