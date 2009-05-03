package org.jetbrains.idea.maven.project.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

public class ToggleOfflineAction extends ToggleAction {

  public boolean isSelected(AnActionEvent e){
    Project project = e.getData(PlatformDataKeys.PROJECT);
    return project != null && MavenProjectsManager.getInstance(project).getGeneralSettings().isWorkOffline();
  }

  public void setSelected(AnActionEvent e, boolean state){
    Project project = e.getData(PlatformDataKeys.PROJECT);
    if(project != null){
      MavenProjectsManager.getInstance(project).getGeneralSettings().setWorkOffline(state);
    }
  }
}