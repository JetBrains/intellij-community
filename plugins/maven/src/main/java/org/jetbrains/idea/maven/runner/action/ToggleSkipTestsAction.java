package org.jetbrains.idea.maven.runner.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.idea.maven.runner.MavenRunner;

public class ToggleSkipTestsAction extends ToggleAction {

  public boolean isSelected(AnActionEvent e){
    Project project = e.getData(PlatformDataKeys.PROJECT);
    return project != null && project.getComponent(MavenRunner.class).getState().isSkipTests();
  }

  public void setSelected(AnActionEvent e, boolean state){
    Project project = e.getData(PlatformDataKeys.PROJECT);
    if(project != null){
      project.getComponent(MavenRunner.class).getState().setSkipTests(state);
    }
  }
}