package com.intellij.ide.macro;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowManager;

public final class LineNumberMacro extends Macro {
  public String getName() {
    return "LineNumber";
  }

  public String getDescription() {
    return IdeBundle.message("macro.line.number");
  }

  public String expand(DataContext dataContext) {
    Project project = DataKeys.PROJECT.getData(dataContext);
    if (project == null) return null;
    if (ToolWindowManager.getInstance(project).isEditorComponentActive()){
      Editor editor = DataKeys.EDITOR.getData(dataContext);
      if (editor != null){
        return String.valueOf(editor.getCaretModel().getLogicalPosition().line + 1);
      }
    }
    return null;
  }
}
