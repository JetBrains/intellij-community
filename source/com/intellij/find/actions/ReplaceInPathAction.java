
package com.intellij.find.actions;

import com.intellij.find.replaceInProject.ReplaceInProjectManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;

public class ReplaceInPathAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Project project = DataKeys.PROJECT.getData(dataContext);
    
    ReplaceInProjectManager.getInstance(project).replaceInProject(dataContext);
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    Project project = DataKeys.PROJECT.getData(event.getDataContext());
    if (project == null) {
      presentation.setEnabled(false);
    }
    else {
      ReplaceInProjectManager replaceManager = ReplaceInProjectManager.getInstance(project);
      presentation.setEnabled(replaceManager.isEnabled());
    }
  }
}
