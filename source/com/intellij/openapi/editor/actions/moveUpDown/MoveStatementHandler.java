/**
 * @author cdr
 */
package com.intellij.openapi.editor.actions.moveUpDown;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;

class MoveStatementHandler extends EditorWriteActionHandler {
  private final boolean isDown;
  private final Mover[] myMovers;

  public MoveStatementHandler(boolean down) {
    isDown = down;
    // order is important
    myMovers = new Mover[]{new StatementMover(down), new DeclarationMover(down), new LineMover(down)};
  }

  public void executeWriteAction(Editor editor, DataContext dataContext) {
    final Project project = editor.getProject();
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    final Document document = editor.getDocument();
    final PsiFile file = documentManager.getPsiFile(document);

    final Mover mover = getSuitableMover(editor, file);
    mover.move(editor,file);
  }

  public boolean isEnabled(Editor editor, DataContext dataContext) {
    if (editor.isOneLineMode()) {
      return false;
    }
    final Project project = editor.getProject();
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    final Document document = editor.getDocument();
    final PsiFile file = documentManager.getPsiFile(document);
    final Mover mover = getSuitableMover(editor, file);
    if (mover == null || mover.insertOffset == -1) return false;
    final int maxLine = editor.offsetToLogicalPosition(editor.getDocument().getTextLength()).line;
    final LineRange range = mover.whatToMove;
    if (range.startLine <= 1 && !isDown) return false;
    return range.endLine < maxLine - 1 || !isDown;
  }

  private Mover getSuitableMover(final Editor editor, final PsiFile file) {
    for (final Mover mover : myMovers) {
      final boolean available = mover.checkAvailable(editor, file);
      if (available) return mover;
    }
    return null;
  }

}

