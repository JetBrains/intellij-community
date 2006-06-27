package com.intellij.ide.bookmarks.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.ide.bookmarks.EditorBookmark;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiFile;

abstract class GotoBookmarkActionBase extends BaseCodeInsightAction implements CodeInsightActionHandler{
  protected GotoBookmarkActionBase() {
    super(false);
  }

  protected CodeInsightActionHandler getHandler() {
    return this;
  }

  public void invoke(Project project, final Editor editor, PsiFile file) {
    if (ToolWindowManager.getInstance(project).isEditorComponentActive()) {
      final EditorBookmark bookmark = getBookmarkToGo(project, editor);
      if (bookmark == null) return;
      if (bookmark.getLineIndex() >= editor.getDocument().getLineCount()) return;
      LogicalPosition pos = new LogicalPosition(bookmark.getLineIndex(), 0);
      editor.getSelectionModel().removeSelection();
      editor.getCaretModel().moveToLogicalPosition(pos);
      editor.getScrollingModel().scrollTo(new LogicalPosition(bookmark.getLineIndex(), 0), ScrollType.CENTER);
    }
  }

  public boolean startInWriteAction() {
    return true;
  }

  protected final boolean isValidForFile(Project project, Editor editor, PsiFile file) {
    return getBookmarkToGo(project, editor) != null;
  }

  abstract protected EditorBookmark getBookmarkToGo(Project project, Editor editor);
}