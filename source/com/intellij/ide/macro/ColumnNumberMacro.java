package com.intellij.ide.macro;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.editor.Editor;

public final class ColumnNumberMacro extends Macro {
  public String getName() {
    return "ColumnNumber";
  }

  public String getDescription() {
    return IdeBundle.message("macro.column.number");
  }

  public String expand(DataContext dataContext) {
    Editor editor = DataKeys.EDITOR.getData(dataContext);
    return editor != null ? String.valueOf(editor.getCaretModel().getLogicalPosition().column + 1) : null;
  }
}
