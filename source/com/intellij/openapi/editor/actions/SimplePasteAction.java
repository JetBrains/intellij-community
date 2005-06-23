/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: May 13, 2002
 * Time: 7:50:36 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.editor.ex.EditorEx;

public class SimplePasteAction extends EditorAction {
  public SimplePasteAction() {
    super(new Handler());
  }

  private static class Handler extends EditorWriteActionHandler {
    public void executeWriteAction(Editor editor, DataContext dataContext) {
      if (editor.isColumnMode()) {
        EditorModificationUtil.pasteFromClipboardAsBlock(editor);
      } else {
        editor.putUserData(EditorEx.LAST_PASTED_REGION, EditorModificationUtil.pasteFromClipboard(editor));
      }
    }
  }
}
