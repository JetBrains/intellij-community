package com.intellij.ide.projectView.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;

public final class ProjectViewActionGroup extends DefaultActionGroup {
  public ProjectViewActionGroup() {
    super();
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    Project project = (Project)event.getDataContext().getData(DataConstants.PROJECT);
    if (project == null) {
      presentation.setVisible(false);
      return;
    }
    String id = ToolWindowManager.getInstance(project).getActiveToolWindowId();
    boolean isProjectViewActive = ToolWindowId.PROJECT_VIEW.equals(id);
    presentation.setVisible(isProjectViewActive);
  }
}