package com.intellij.xdebugger.impl.actions.handlers;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.impl.actions.DebuggerActionHandler;
import com.intellij.xdebugger.breakpoints.XLineBreakpointType;
import com.intellij.xdebugger.XDebuggerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public class XToggleLineBreakpointActionHandler extends DebuggerActionHandler {

  public boolean isEnabled(@NotNull final Project project, final AnActionEvent event) {
    Editor editor = getEditor(project, event);
    if (editor == null) return false;

    final Document document = editor.getDocument();
    final int offset = editor.getCaretModel().getOffset();
    int line = document.getLineNumber(offset);

    VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    XLineBreakpointType<?>[] breakpointTypes = XDebuggerUtil.getInstance().getLineBreakpointTypes();
    for (XLineBreakpointType<?> breakpointType : breakpointTypes) {
      if (breakpointType.canPutAt(file, line)) {
        return true;
      }
    }
    return false;
  }

  public void perform(@NotNull final Project project, final AnActionEvent event) {
    Editor editor = getEditor(project, event);
    if (editor == null) return;

    Document document = editor.getDocument();
    int line = document.getLineNumber(editor.getCaretModel().getOffset());
    VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    for (XLineBreakpointType<?> type : XDebuggerUtil.getInstance().getLineBreakpointTypes()) {
      if (type.canPutAt(file, line)) {
        XDebuggerUtil.getInstance().toggleLineBreakpoint(project, type, file, line);
        return;
      }
    }
  }

  @Nullable
  private static Editor getEditor(Project project, AnActionEvent event) {
    Editor editor = event.getData(DataKeys.EDITOR);
    if(editor == null) {
      return FileEditorManager.getInstance(project).getSelectedTextEditor();
    }
    return editor;
  }
}
