package com.intellij.ide.macro;

import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;

public final class ColumnNumberMacro extends Macro {
  public String getName() {
    return "ColumnNumber";
  }

  public String getDescription() {
    return "Column number";
  }

  public String expand(DataContext dataContext) {
    Editor editor = (Editor)dataContext.getData(DataConstants.EDITOR);
    return editor != null ? String.valueOf(editor.getCaretModel().getLogicalPosition().column + 1) : null;
  }
}
