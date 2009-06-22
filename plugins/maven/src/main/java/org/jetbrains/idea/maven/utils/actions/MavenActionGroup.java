package org.jetbrains.idea.maven.utils.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;

public class MavenActionGroup extends DefaultActionGroup {
  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    e.getPresentation().setEnabled(isAvailable(e));
  }

  protected boolean isAvailable(AnActionEvent e) {
    return MavenActionUtils.getProject(e) != null;
  }
}
