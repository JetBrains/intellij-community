/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: May 13, 2002
 * Time: 5:37:50 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;

public class CopyAction extends EditorAction {
  public CopyAction() {
    super(new Handler());
  }

  public static class Handler extends EditorActionHandler {
    public void execute(Editor editor, DataContext dataContext) {
      if (!editor.getSelectionModel().hasSelection() && !editor.getSelectionModel().hasBlockSelection()) {
        editor.getSelectionModel().selectLineAtCaret();
      }
      editor.getSelectionModel().copySelectionToClipboard();
    }
  }
}
