
package com.intellij.debugger.actions;

import com.intellij.debugger.DebuggerManager;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;

public class ResumeAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    Project project = (Project)e.getDataContext().getData(DataConstants.PROJECT);
    if (project == null) {
      return;
    }
    (DebuggerManagerEx.getInstanceEx(project)).getContext().getDebuggerSession().resume();
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    Project project = (Project)event.getDataContext().getData(DataConstants.PROJECT);
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }
    DebuggerSession debuggerSession = (DebuggerManagerEx.getInstanceEx(project)).getContext().getDebuggerSession();
    presentation.setEnabled(debuggerSession != null && debuggerSession.isPaused());
  }
}
