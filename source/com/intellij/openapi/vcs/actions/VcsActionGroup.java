package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;

/**
 * @author mike
 */
public class VcsActionGroup extends DefaultActionGroup {
  public VcsActionGroup() {
    super();
  }

  public void update(AnActionEvent event) {
    super.update(event);

    Presentation presentation = event.getPresentation();
    Project project = (Project)event.getDataContext().getData(DataConstants.PROJECT);
    if (project == null){
      presentation.setVisible(false);
      presentation.setEnabled(false);
    } else if (ProjectLevelVcsManager.getInstance(project).getAllActiveVcss().length == 0){
      presentation.setVisible(false);
      presentation.setEnabled(false);
    } else {
      presentation.setVisible(true);
      presentation.setEnabled(true);
    }
  }
}
