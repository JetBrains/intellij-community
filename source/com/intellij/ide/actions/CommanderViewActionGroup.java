package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;

public class CommanderViewActionGroup extends DefaultActionGroup {
  public CommanderViewActionGroup() {
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
    boolean isCommanderActive = ToolWindowId.COMMANDER.equals(id);
    presentation.setVisible(isCommanderActive);
  }
}