/*
 * Class EvaluateAction
 * @author Jeka
 */
package com.intellij.debugger.actions;

import com.intellij.debugger.DebuggerManager;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.ui.ValueHint;
import com.intellij.debugger.ui.ValueLookupManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.project.Project;

public class QuickEvaluateAction extends AnAction {

  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null) { return; }

    DebuggerSession debuggerSession = (DebuggerManagerEx.getInstanceEx(project)).getContext().getDebuggerSession();
    if (debuggerSession == null || !debuggerSession.isPaused()) return;

    Editor editor = (Editor)e.getDataContext().getData(DataConstants.EDITOR);

    if(editor != null) {
      LogicalPosition logicalPosition = editor.getCaretModel().getLogicalPosition();
      ValueLookupManager.getInstance(project).showHint(editor, editor.logicalPositionToXY(logicalPosition), ValueHint.MOUSE_CLICK_HINT);
    }
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    Project project = (Project)event.getDataContext().getData(DataConstants.PROJECT);
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }

    DebuggerSession debuggerSession = (DebuggerManagerEx.getInstanceEx(project)).getContext().getDebuggerSession();

    boolean toEnable = debuggerSession != null && debuggerSession.isPaused();
    presentation.setEnabled(toEnable);
    if (ActionPlaces.EDITOR_POPUP.equals(event.getPlace())) {
      presentation.setVisible(toEnable);
    }
    else {
      presentation.setVisible(true);
    }
  }
}
