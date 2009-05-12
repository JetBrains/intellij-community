package org.jetbrains.idea.maven.utils;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.tasks.MavenTasksManager;

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

  protected MavenTasksManager getTasksManager(AnActionEvent e) {
    return MavenTasksManager.getInstance(getProject(e));
  }
}