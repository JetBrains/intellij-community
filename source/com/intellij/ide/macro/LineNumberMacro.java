package com.intellij.ide.macro;

import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowManager;

public final class LineNumberMacro extends Macro {
  public String getName() {
    return "LineNumber";
  }

  public String getDescription() {
    return "Line number";
  }

  public String expand(DataContext dataContext) {
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null) return null;
    if (ToolWindowManager.getInstance(project).isEditorComponentActive()){
      Editor editor = (Editor)dataContext.getData(DataConstants.EDITOR);
      if (editor != null){
        return String.valueOf(editor.getCaretModel().getLogicalPosition().line + 1);
      }
    }
    return null;
  }
}
