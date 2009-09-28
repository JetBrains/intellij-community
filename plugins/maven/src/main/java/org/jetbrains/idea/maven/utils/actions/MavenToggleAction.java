package org.jetbrains.idea.maven.utils.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;

public abstract class MavenToggleAction extends ToggleAction implements DumbAware {
  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    e.getPresentation().setEnabled(isAvailable(e));
  }

  protected boolean isAvailable(AnActionEvent e) {
    return MavenActionUtil.getProject(e) != null;
  }

  public final boolean isSelected(AnActionEvent e) {
    if (!isAvailable(e)) return false;
    return doIsSelected(e);
  }

  protected abstract boolean doIsSelected(AnActionEvent e);
}