package org.jetbrains.idea.maven.utils;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

public abstract class MavenToggleAction extends ToggleAction implements DumbAware {
  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    e.getPresentation().setEnabled(isAvailable(e));
  }

  protected boolean isAvailable(AnActionEvent e) {
    return getProject(e) != null;
  }

  public final boolean isSelected(AnActionEvent e) {
    if (!isAvailable(e)) return false;
    return doIsSelected(e);
  }

  protected abstract boolean doIsSelected(AnActionEvent e);

  protected Project getProject(AnActionEvent e) {
    return e.getData(PlatformDataKeys.PROJECT);
  }

  protected MavenProjectsManager getProjectsManager(AnActionEvent e) {
    return MavenProjectsManager.getInstance(getProject(e));
  }

  protected VirtualFile getMavenProjectFile(AnActionEvent e) {
    VirtualFile file = e.getData(PlatformDataKeys.VIRTUAL_FILE);
    return MavenUtil.isMavenProjectFile(file) ? file : null;
  }
}