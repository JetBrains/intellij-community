package org.jetbrains.idea.maven.project.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.idea.maven.utils.MavenToggleAction;

public class ToggleOfflineAction extends MavenToggleAction {
  @Override
  protected boolean doIsSelected(AnActionEvent e) {
    return getProjectsManager(e).getGeneralSettings().isWorkOffline();
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    getProjectsManager(e).getGeneralSettings().setWorkOffline(state);
  }
}