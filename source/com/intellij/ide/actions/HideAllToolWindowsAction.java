package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;

public class HideAllToolWindowsAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    Project project = (Project)e.getDataContext().getData(DataConstants.PROJECT);
    if (project == null) {
      return;
    }

    ToolWindowManagerEx toolWindowManager = ToolWindowManagerEx.getInstanceEx(project);

    // to clear windows stack
    toolWindowManager.clearSideStack();
    //toolWindowManager.activateEditorComponent();
    
    String[] ids = toolWindowManager.getToolWindowIds();
    for (int i = 0; i < ids.length; i++) {
      String id = ids[i];
      ToolWindow toolWindow = toolWindowManager.getToolWindow(id);
      if (toolWindow.isVisible()) {
        toolWindow.hide(null);
      }
    }
    toolWindowManager.activateEditorComponent();
  }

  public void update(AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    Project project = (Project)event.getDataContext().getData(DataConstants.PROJECT);
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }

    ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
    String[] ids = toolWindowManager.getToolWindowIds();
    for (int i = 0; i < ids.length; i++) {
      String id = ids[i];
      if (toolWindowManager.getToolWindow(id).isVisible()) {
        presentation.setEnabled(true);
        return;
      }
    }

    presentation.setEnabled(false);
  }
}