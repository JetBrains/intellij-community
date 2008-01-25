/*
 * Class ViewBreakpointsAction
 * @author Jeka
 */
package com.intellij.xdebugger.impl.actions;

import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.xdebugger.impl.DebuggerSupport;
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointsConfigurationDialogFactory;
import org.jetbrains.annotations.Nullable;

public class ViewBreakpointsAction extends AnAction {
  private Object myInitialBreakpoint;

  public ViewBreakpointsAction(){
    this(ActionsBundle.actionText(XDebuggerActions.VIEW_BREAKPOINTS), null);
  }

  public ViewBreakpointsAction(String name, Object initialBreakpoint) {
    super(name);
    myInitialBreakpoint = initialBreakpoint;
  }

  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) return;

    if (myInitialBreakpoint == null) {
      Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
      if (editor != null) {
        myInitialBreakpoint = findSelectedBreakpoint(project, editor);
      }
    }

    DialogWrapper dialog = BreakpointsConfigurationDialogFactory.getInstance(project).createDialog(myInitialBreakpoint);
    dialog.show();
    myInitialBreakpoint = null;
  }

  @Nullable
  private static Object findSelectedBreakpoint(final Project project, final Editor editor) {
    int offset = editor.getCaretModel().getOffset();
    Document editorDocument = editor.getDocument();

    DebuggerSupport[] debuggerSupports = DebuggerSupport.getDebuggerSupports();
    for (DebuggerSupport debuggerSupport : debuggerSupports) {
      Object breakpoint = debuggerSupport.getBreakpointPanelProvider().findBreakpoint(project, editorDocument, offset);
      if (breakpoint != null) {
        return breakpoint;
      }
    }
    return null;
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    Project project = PlatformDataKeys.PROJECT.getData(event.getDataContext());
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }
    presentation.setEnabled(true);
  }

}