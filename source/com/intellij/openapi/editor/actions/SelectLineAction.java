/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: May 22, 2002
 * Time: 10:43:26 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.actionSystem.DataContext;

public class SelectLineAction extends EditorAction {
  public SelectLineAction() {
    super(new Handler());
  }

  private static class Handler extends EditorActionHandler {
    public void execute(Editor editor, DataContext dataContext) {
      editor.getSelectionModel().selectLineAtCaret();
    }
  }
}
