/**
 * @author cdr
 */
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;

class MoveStatementHandler extends EditorWriteActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.actions.MoveStatementHandler");

  private final boolean isDown;

  public MoveStatementHandler(boolean down) {
    isDown = down;
  }

  public void executeWriteAction(Editor editor, DataContext dataContext) {
    final Project project = editor.getProject();
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    final Document document = editor.getDocument();
    final PsiFile file = documentManager.getPsiFile(document);

    final CaretModel caretModel = editor.getCaretModel();
    final int caretColumn = caretModel.getLogicalPosition().column;
    final int caretLine = caretModel.getLogicalPosition().line;
    final LineRange lineRange = getRangeToMove(editor,file);
    final int startLine = lineRange.startLine;
    final int endLine = lineRange.endLine;
    final int insertOffset = editor.logicalPositionToOffset(new LogicalPosition(isDown ? endLine + 2 : startLine - 1, 0));

    final int start = editor.logicalPositionToOffset(new LogicalPosition(startLine, 0));
    final int end = editor.logicalPositionToOffset(new LogicalPosition(endLine+1, 0));
    final String toInsert = document.getCharsSequence().subSequence(start, end).toString();
    final int insStart = isDown ? insertOffset - toInsert.length() : insertOffset;
    final int insEnd = isDown ? insertOffset : insertOffset + toInsert.length();

    final SelectionModel selectionModel = editor.getSelectionModel();
    final int selectionStart = selectionModel.getSelectionStart();
    final int selectionEnd = selectionModel.getSelectionEnd();
    final boolean hasSelection = selectionModel.hasSelection();

    // for preventing flicker
    caretModel.moveToOffset(0);

    document.deleteString(start, end);
    document.insertString(insStart, toInsert);
    documentManager.commitDocument(document);

    caretModel.moveToLogicalPosition(new LogicalPosition(caretLine + (isDown ? 1 : -1), caretColumn));
    if (hasSelection) {
      restoreSelection(editor, selectionStart, selectionEnd, start, insStart);
    }

    try {
      final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
      final int line1 = editor.offsetToLogicalPosition(insStart).line;
      final int line2 = editor.offsetToLogicalPosition(insEnd).line;
      for (int line = line1; line <= line2; line++) {
        int lineStart = document.getLineStartOffset(line);
        codeStyleManager.adjustLineIndent(file, lineStart);
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }

    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
  }

  private static void restoreSelection(final Editor editor, final int selectionStart, final int selectionEnd, final int moveOffset, int insOffset) {
    final int selectionRelativeOffset = selectionStart - moveOffset;
    int newSelectionStart = insOffset + selectionRelativeOffset;
    int newSelectionEnd = newSelectionStart + selectionEnd - selectionStart;
    editor.getSelectionModel().setSelection(newSelectionStart, newSelectionEnd);
  }

  public boolean isEnabled(Editor editor, DataContext dataContext) {
    if (editor.isOneLineMode()) {
      return false;
    }
    final Project project = editor.getProject();
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    final Document document = editor.getDocument();
    final PsiFile file = documentManager.getPsiFile(document);
    return getRangeToMove(editor,file) != null;
  }

  private LineRange getRangeToMove(final Editor editor, final PsiFile file) {
    final SelectionModel selectionModel = editor.getSelectionModel();
    final int startLine;
    final int endLine;
    LineRange result;
    if (selectionModel.hasSelection()) {
      startLine = editor.offsetToLogicalPosition(selectionModel.getSelectionStart()).line;
      final LogicalPosition endPos = editor.offsetToLogicalPosition(selectionModel.getSelectionEnd());
      endLine = endPos.column == 0 ? endPos.line - 1 : endPos.line;
      result = new LineRange(startLine, endLine);
    }
    else {
      startLine = editor.getCaretModel().getLogicalPosition().line;
      endLine = startLine;
      result = new LineRange(startLine, endLine);
    }
    if (file instanceof PsiJavaFile) {
      result = expandLineRangeToStatement(result, editor,file);
      if (result == null) return null;
    }
    final int maxLine = editor.offsetToLogicalPosition(editor.getDocument().getTextLength()).line;
    if (result.startLine <= 1 && !isDown) return null;
    if (result.endLine >= maxLine - 1 && isDown) return null;

    final PsiElement elementAt = file.findElementAt(editor.getCaretModel().getOffset());
    if (elementAt == null) return null;
    final PsiElement guard = PsiTreeUtil.getParentOfType(elementAt, new Class[]{PsiMethod.class, PsiClassInitializer.class, PsiClass.class});
    // move operation should not go out of method
    final int insertOffset = editor.logicalPositionToOffset(new LogicalPosition(isDown ? result.endLine + 2 : result.startLine - 1, 0));
    if (guard != null && !guard.getTextRange().contains(insertOffset)) return null;
    
    return result;
  }

  private static LineRange expandLineRangeToStatement(final LineRange range, Editor editor, final PsiFile file) {
    final int startOffset = editor.logicalPositionToOffset(new LogicalPosition(range.startLine, 0));
    PsiElement startingElement = firstNonWhiteElement(startOffset, file, true);
    if (startingElement == null) return null;
    final int endOffset = editor.logicalPositionToOffset(new LogicalPosition(range.endLine+1, 0)) -1;

    PsiElement endingElement = firstNonWhiteElement(endOffset, file, false);
    if (endingElement == null) return null;
    final PsiElement element = PsiTreeUtil.findCommonParent(startingElement, endingElement);
    Pair<PsiElement, PsiElement> elementRange = getElementRange(element, startingElement, endingElement);
    return new LineRange(editor.offsetToLogicalPosition(elementRange.getFirst().getTextOffset()).line,
                     editor.offsetToLogicalPosition(elementRange.getSecond().getTextRange().getEndOffset()).line);
  }

  private static Pair<PsiElement, PsiElement> getElementRange(final PsiElement parent,
                                                              PsiElement element1,
                                                              PsiElement element2) {
    if (PsiTreeUtil.isAncestor(element1, element2, false) || PsiTreeUtil.isAncestor(element2, element1, false)) {
      return Pair.create(parent, parent);
    }
    // find nearset children that are parents of elements
    while (element1.getParent() != parent) {
      element1 = element1.getParent();
    }
    while (element2.getParent() != parent) {
      element2 = element2.getParent();
    }
    return Pair.create(element1, element2);
  }

  private static PsiElement firstNonWhiteElement(int offset, PsiFile file, final boolean lookRight) {
    PsiElement element = file.findElementAt(offset);
    if (element instanceof PsiWhiteSpace) {
      element = lookRight ? element.getNextSibling() : element.getPrevSibling();
    }
    return element;
  }

  private static class LineRange {
    private final int startLine;
    private final int endLine;

    public LineRange(final int startLine, final int endLine) {
      this.startLine = startLine;
      this.endLine = endLine;
    }
  }
}