package com.intellij.openapi.editor.actions.moveUpDown;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.codeInsight.CodeInsightUtil;

class StatementMover extends LineMover {
  public LineRange getRangeToMove(Editor editor, PsiFile file, boolean isDown) {
    if (!(file instanceof PsiJavaFile)) return null;
    LineRange result = super.getRangeToMove(editor, file, isDown);

    result = expandLineRangeToCoverPsiElements(result, editor, file);
    if (result == null) return null;
    final int startOffset = editor.logicalPositionToOffset(new LogicalPosition(result.startLine, 0));
    final int endOffset = editor.logicalPositionToOffset(new LogicalPosition(result.endLine+1, 0));
    final PsiElement[] statements = CodeInsightUtil.findStatementsInRange(file, startOffset, endOffset);
    if (statements == null || statements.length == 0) return null;
    result.firstElement = statements[0];
    result.lastElement = statements[statements.length-1];
    return result;
  }

  public int getOffsetToMoveTo(Editor editor, PsiFile file, LineRange range, boolean isDown) {
    if (!checkMovingInsideOutside(file, editor, range, isDown)) return -1;
    return calcInsertOffset(editor, range.startLine, range.endLine, isDown);
  }

  private static int calcInsertOffset(final Editor editor, final int startLine, final int endLine, final boolean isDown) {
    int nearLine = isDown ? endLine + 2 : startLine - 1;
    int line = nearLine;
    final PsiFile file = PsiDocumentManager.getInstance(editor.getProject()).getPsiFile(editor.getDocument());
    if (!(file instanceof PsiJavaFile)) {
      return editor.logicalPositionToOffset(new LogicalPosition(nearLine, 0));
    }

    while (true) {
      final int offset = editor.logicalPositionToOffset(new LogicalPosition(line, 0));
      PsiElement element = firstNonWhiteElement(offset, file, true);
      while (element != null && element != file) {
        if (!element.getTextRange().contains(offset)) {
          if ((element instanceof PsiStatement || element instanceof PsiComment)
              && element.getParent() instanceof PsiCodeBlock) {
            return offset;
          }
          if (element instanceof PsiJavaToken
              && ((PsiJavaToken)element).getTokenType() == JavaTokenType.RBRACE
              && element.getParent() instanceof PsiCodeBlock) {
            return offset;
          }
          if (element instanceof PsiMember) {
            return offset;
          }
        }
        element = element.getParent();
      }
      line += isDown ? 1 : -1;
      if (line == 0 || line >= editor.getDocument().getLineCount()) {
        return editor.logicalPositionToOffset(new LogicalPosition(nearLine, 0));
      }
    }
  }

  private static boolean checkMovingInsideOutside(final PsiFile file, final Editor editor, final LineRange result, final boolean isDown) {
    final int offset = editor.getCaretModel().getOffset();
    PsiElement elementAt = file.findElementAt(offset);
    if (elementAt == null) return false;

    final Class[] classes = new Class[]{PsiMethod.class, PsiClassInitializer.class, PsiClass.class, PsiComment.class,};
    final PsiElement guard = PsiTreeUtil.getParentOfType(elementAt, classes);
    // cannot move in/outside method/class/initializer/comment
    final int insertOffset = calcInsertOffset(editor, result.startLine, result.endLine, isDown);
    elementAt = file.findElementAt(insertOffset);
    final PsiElement newGuard = PsiTreeUtil.getParentOfType(elementAt, classes);
    if (newGuard == guard && isInside(insertOffset, newGuard) == isInside(offset, guard)) return true;

    // moving in/out nested class is OK
    if (guard instanceof PsiClass && guard.getParent() instanceof PsiClass) return true;
    if (newGuard instanceof PsiClass && newGuard.getParent() instanceof PsiClass) return true;
    return false;
  }

  private static boolean isInside(final int offset, final PsiElement guard) {
    if (guard == null) return false;
    TextRange inside = guard instanceof PsiMethod ? ((PsiMethod)guard).getBody().getTextRange() : guard instanceof PsiClassInitializer
      ? ((PsiClassInitializer)guard).getBody().getTextRange()
      : guard instanceof PsiClass
      ? new TextRange(((PsiClass)guard).getLBrace().getTextOffset(), ((PsiClass)guard).getRBrace().getTextOffset())
      : guard.getTextRange();
    return inside != null && inside.contains(offset);
  }

  private static LineRange expandLineRangeToCoverPsiElements(final LineRange range, Editor editor, final PsiFile file) {
    Pair<PsiElement, PsiElement> psiRange = getElementRange(editor, file, range);
    if (psiRange == null) return null;
    final PsiElement parent = PsiTreeUtil.findCommonParent(psiRange.getFirst(), psiRange.getSecond());
    Pair<PsiElement, PsiElement> elementRange = getElementRange(parent, psiRange.getFirst(), psiRange.getSecond());
    return new LineRange(editor.offsetToLogicalPosition(elementRange.getFirst().getTextOffset()).line,
                     editor.offsetToLogicalPosition(elementRange.getSecond().getTextRange().getEndOffset()).line);
  }
}
