package org.jetbrains.idea.maven.builder.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.idea.maven.builder.MavenBuilder;

public class ToggleSkipTestsAction extends ToggleAction {

  public boolean isSelected(AnActionEvent e){
    Project project = e.getData(DataKeys.PROJECT);
    return project != null && project.getComponent(MavenBuilder.class).getState().isSkipTests();
  }

  public void setSelected(AnActionEvent e, boolean state){
    Project project = e.getData(DataKeys.PROJECT);
    if(project != null){
      project.getComponent(MavenBuilder.class).getState().setSkipTests(state);
    }
  }
}