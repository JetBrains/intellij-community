package org.jetbrains.idea.maven.project.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.idea.maven.utils.actions.MavenToggleAction;
import org.jetbrains.idea.maven.utils.actions.MavenActionUtils;

public class ToggleOfflineAction extends MavenToggleAction {
  @Override
  protected boolean doIsSelected(AnActionEvent e) {
    return MavenActionUtils.getProjectsManager(e).getGeneralSettings().isWorkOffline();
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    MavenActionUtils.getProjectsManager(e).getGeneralSettings().setWorkOffline(state);
  }
}