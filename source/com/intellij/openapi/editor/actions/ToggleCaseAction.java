/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: May 20, 2002
 * Time: 4:13:37 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;

public class ToggleCaseAction extends EditorAction {
  public ToggleCaseAction() {
    super(new Handler());
  }

  private static class Handler extends EditorWriteActionHandler {
    public void executeWriteAction(Editor editor, DataContext dataContext) {
      if (!editor.getSelectionModel().hasSelection()) {
        editor.getSelectionModel().selectWordAtCaret(true);
      }

      int startOffset = editor.getSelectionModel().getSelectionStart();
      int endOffset = editor.getSelectionModel().getSelectionEnd();

      String text = editor.getDocument().getCharsSequence().subSequence(startOffset, endOffset).toString();
      String lowered = text.toLowerCase();
      if (text.equals(lowered)) lowered = lowered.toUpperCase();

      editor.getDocument().replaceString(startOffset, endOffset, lowered);
      editor.getSelectionModel().setSelection(startOffset, endOffset);
    }
  }
}
