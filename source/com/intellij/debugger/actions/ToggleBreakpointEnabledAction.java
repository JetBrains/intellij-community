package com.intellij.debugger.actions;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.ui.breakpoints.Breakpoint;
import com.intellij.debugger.ui.breakpoints.BreakpointManager;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;

public class ToggleBreakpointEnabledAction extends AnAction {

  public ToggleBreakpointEnabledAction() {
    super("Toggle Breakpoint Enabled");
  }

  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    Breakpoint breakpoint = findBreakpoint(dataContext);
    breakpoint.ENABLED = !breakpoint.ENABLED;
    DebuggerManagerEx.getInstanceEx(project).getBreakpointManager().breakpointChanged(breakpoint);
    breakpoint.updateUI();
  }

  private Breakpoint findBreakpoint(DataContext dataContext) {
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
    if(editor == null) return null;
    BreakpointManager manager = (DebuggerManagerEx.getInstanceEx(project)).getBreakpointManager();
    int offset = editor.getCaretModel().getOffset();
    return manager.findBreakpoint(editor.getDocument(), offset);
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }

    Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
    if (editor == null) {
      presentation.setEnabled(false);
      return;
    }
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (file == null) {
      presentation.setEnabled(false);
      return;
    }

    FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    FileType fileType = fileTypeManager.getFileTypeByFile(file.getVirtualFile());
    if(StdFileTypes.JAVA != fileType && StdFileTypes.JSP != fileType){
      presentation.setEnabled(false);
      return;
    }

    Breakpoint breakpoint = findBreakpoint(dataContext);
    if (breakpoint == null) {
      presentation.setEnabled(false);
      return;
    }
    presentation.setEnabled(true);
  }
}
