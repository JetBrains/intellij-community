/*
 * Class ViewBreakpointsAction
 * @author Jeka
 */
package com.intellij.debugger.actions;

import com.intellij.debugger.DebuggerManager;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.ui.breakpoints.Breakpoint;
import com.intellij.debugger.ui.breakpoints.BreakpointManager;
import com.intellij.debugger.ui.breakpoints.BreakpointPropertiesPanel;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.idea.ActionsBundle;

public class ViewBreakpointsAction extends AnAction {
  private Breakpoint myInitialBreakpoint;

  public ViewBreakpointsAction(){
    this(ActionsBundle.actionText(DebuggerActions.VIEW_BREAKPOINTS));
  }

  public ViewBreakpointsAction(String name) {
    super(name);
  }

  public void setInitialBreakpoint(Breakpoint breakpoint) {
    myInitialBreakpoint = breakpoint;
  }

  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null) return;
    DebuggerManagerEx debugManager = DebuggerManagerEx.getInstanceEx(project);

    if (myInitialBreakpoint == null) {
      Editor editor = (Editor)dataContext.getData(DataConstants.EDITOR);
      if (editor != null) {
        BreakpointManager manager = debugManager.getBreakpointManager();
        int offset = editor.getCaretModel().getOffset();
        Document editorDocument = editor.getDocument();
        myInitialBreakpoint = manager.findBreakpoint(editorDocument, offset);
      }
    }

    DialogWrapper dialog = debugManager.getBreakpointManager().createConfigurationDialog(myInitialBreakpoint, null);
    dialog.show();
    myInitialBreakpoint = null;
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    Project project = (Project)event.getDataContext().getData(DataConstants.PROJECT);
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }
    presentation.setEnabled(true);
  }

}