
package com.intellij.find.actions;

import com.intellij.find.findInProject.FindInProjectManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;

public class FindInPathAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Project project = e.getData(DataKeys.PROJECT);
    
    FindInProjectManager.getInstance(project).findInProject(dataContext);
  }

  public void update(AnActionEvent e){
    Presentation presentation = e.getPresentation();
    Project project = e.getData(DataKeys.PROJECT);
    presentation.setEnabled(project != null);
    if (project != null) {
      FindInProjectManager findManager = FindInProjectManager.getInstance(project);
      presentation.setEnabled(findManager.isEnabled());
    }
  }
}
