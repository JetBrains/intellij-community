package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;

/**
 * @author max
 */
public class SplitLineAction extends EditorAction {
  public SplitLineAction() {
    super(new Handler());
  }

  private static class Handler extends EditorWriteActionHandler {
    public boolean isEnabled(Editor editor, DataContext dataContext) {
      return getEnterHandler().isEnabled(editor, dataContext);
    }

    public void executeWriteAction(Editor editor, DataContext dataContext) {
      LogicalPosition caretPosition = editor.getCaretModel().getLogicalPosition();

      getEnterHandler().execute(editor, dataContext);

      editor.getCaretModel().moveToLogicalPosition(caretPosition);
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    }

    private EditorActionHandler getEnterHandler() {
      return EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_ENTER);
    }
  }
}
