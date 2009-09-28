package org.jetbrains.idea.maven.project.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.idea.maven.utils.actions.MavenToggleAction;
import org.jetbrains.idea.maven.utils.actions.MavenActionUtil;

public class ToggleOfflineAction extends MavenToggleAction {
  @Override
  protected boolean doIsSelected(AnActionEvent e) {
    return MavenActionUtil.getProjectsManager(e).getGeneralSettings().isWorkOffline();
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    MavenActionUtil.getProjectsManager(e).getGeneralSettings().setWorkOffline(state);
  }
}