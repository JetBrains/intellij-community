
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;

public class JumpToLastWindowAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    Project project = (Project)e.getDataContext().getData(DataConstants.PROJECT);
    if (project == null) {
      return;
    }
    ToolWindowManagerEx manager = ToolWindowManagerEx.getInstanceEx(project);
    String id = manager.getLastActiveToolWindowId();
    if(id==null||!manager.getToolWindow(id).isAvailable()){
      return;
    }
    manager.getToolWindow(id).activate(null);
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    Project project = (Project)event.getDataContext().getData(DataConstants.PROJECT);
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }
    ToolWindowManagerEx manager=(ToolWindowManagerEx)ToolWindowManager.getInstance(project);
    String id = manager.getLastActiveToolWindowId();
    presentation.setEnabled(id != null && manager.getToolWindow(id).isAvailable());
  }
}
