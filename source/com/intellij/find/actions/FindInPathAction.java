
package com.intellij.find.actions;

import com.intellij.find.findInProject.FindInProjectManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;

public class FindInPathAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    
    FindInProjectManager.getInstance(project).findInProject(dataContext);
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    Project project = (Project)event.getDataContext().getData(DataConstants.PROJECT);
    if (project == null) {
      presentation.setEnabled(false);
    } else {
      FindInProjectManager findManager = FindInProjectManager.getInstance(project);
      presentation.setEnabled(findManager.isEnabled());
    }
  }
}
