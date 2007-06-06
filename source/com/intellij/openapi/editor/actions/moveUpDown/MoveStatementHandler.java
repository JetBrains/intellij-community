/**
 * @author cdr
 */
package com.intellij.openapi.editor.actions.moveUpDown;

import com.intellij.lang.ASTNode;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtil;

class MoveStatementHandler extends EditorWriteActionHandler {
  private final boolean isDown;

  public MoveStatementHandler(boolean down) {
    isDown = down;
  }

  public void executeWriteAction(Editor editor, DataContext dataContext) {
    final Project project = editor.getProject();
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    final Document document = editor.getDocument();
    PsiFile file = getRoot(documentManager.getPsiFile(document), editor);

    final Mover mover = getSuitableMover(editor, file);
    mover.move(editor,file);
  }

  public boolean isEnabled(Editor editor, DataContext dataContext) {
    if (editor.isViewer() || editor.isOneLineMode()) return false;
    final Project project = editor.getProject();
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    final Document document = editor.getDocument();
    documentManager.commitDocument(document);
    PsiFile psiFile = documentManager.getPsiFile(document);
    PsiFile file = getRoot(psiFile, editor);
    if (file == null) return false;
    final Mover mover = getSuitableMover(editor, file);
    if (mover == null || mover.toMove2 == null) return false;
    final int maxLine = editor.offsetToLogicalPosition(editor.getDocument().getTextLength()).line;
    final LineRange range = mover.toMove;
    if (range.startLine == 0 && !isDown) return false;
    //
    return range.endLine < maxLine || !isDown;
  }

  private static PsiFile getRoot(final PsiFile file, final Editor editor) {
    if (file == null) return null;
    int offset = editor.getCaretModel().getOffset();
    if (offset == editor.getDocument().getTextLength()) offset--;
    if (offset<0) return null;
    PsiElement leafElement = file.findElementAt(offset);
    if (leafElement == null) return null;
    if (leafElement.getLanguage() == StdLanguages.ANT) {
      leafElement = file.getViewProvider().findElementAt(offset, StdLanguages.XML);
      if (leafElement == null) return null;
    }
    ASTNode node = leafElement.getNode();
    if (node == null) return null;
    return (PsiFile)PsiUtil.getRoot(node).getPsi();
  }

  private Mover getSuitableMover(final Editor editor, final PsiFile file) {
    // order is important
    Mover[] movers = new Mover[]{new StatementMover(isDown), new DeclarationMover(isDown), new XmlMover(isDown), new LineMover(isDown)};
    for (final Mover mover : movers) {
      final boolean available = mover.checkAvailable(editor, file);
      if (available) return mover;
    }
    return null;
  }

}

