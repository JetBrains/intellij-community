/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: May 13, 2002
 * Time: 6:41:17 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.actionSystem.DataContext;

public class CutAction extends EditorAction {
  public CutAction() {
    super(new Handler());
  }

  public static class Handler extends EditorWriteActionHandler {
    public void executeWriteAction(Editor editor, DataContext dataContext) {
      if(!editor.getSelectionModel().hasSelection()) {
        editor.getSelectionModel().selectLineAtCaret();
      }
      editor.getSelectionModel().copySelectionToClipboard();
      EditorModificationUtil.deleteSelectedText(editor);
    }
  }
}