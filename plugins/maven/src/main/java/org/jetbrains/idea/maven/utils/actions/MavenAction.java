package org.jetbrains.idea.maven.utils.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAware;

public abstract class MavenAction extends AnAction implements DumbAware {
  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    Presentation p = e.getPresentation();
    p.setEnabled(isAvailable(e));
    p.setVisible(isVisible(e));
  }

  protected boolean isAvailable(AnActionEvent e) {
    return MavenActionUtil.getProject(e) != null;
  }

  protected boolean isVisible(AnActionEvent e) {
    return true;
  }
}