/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: May 15, 2002
 * Time: 8:25:25 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;

public class EscapeAction extends EditorAction {
  public EscapeAction() {
    super(new Handler());
  }

  private static class Handler extends EditorActionHandler {
    public void execute(Editor editor, DataContext dataContext) {
      editor.getSelectionModel().removeSelection();
    }

    public boolean isEnabled(Editor editor, DataContext dataContext) {
      SelectionModel selectionModel = editor.getSelectionModel();
      return dataContext.getData(DataConstants.IS_MODAL_CONTEXT) != Boolean.TRUE &&
             (selectionModel.hasSelection() || selectionModel.hasBlockSelection());
    }
  }
}
