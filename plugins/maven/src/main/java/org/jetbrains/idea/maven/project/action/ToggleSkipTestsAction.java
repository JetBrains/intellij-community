package org.jetbrains.idea.maven.project.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import org.jetbrains.idea.maven.runner.MavenRunner;
import org.jetbrains.idea.maven.utils.MavenToggleAction;

public class ToggleSkipTestsAction extends MavenToggleAction {
  public boolean isSelected(AnActionEvent e){
    Project project = e.getData(PlatformDataKeys.PROJECT);
    return project != null && MavenRunner.getInstance(project).getState().isSkipTests();
  }

  public void setSelected(AnActionEvent e, boolean state){
    Project project = e.getData(PlatformDataKeys.PROJECT);
    if(project != null){
      MavenRunner.getInstance(project).getState().setSkipTests(state);
    }
  }
}