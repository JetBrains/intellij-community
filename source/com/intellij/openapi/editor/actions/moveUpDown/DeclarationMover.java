package com.intellij.openapi.editor.actions.moveUpDown;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;

class DeclarationMover extends LineMover {
  public LineRange getRangeToMove(Editor editor, PsiFile file, boolean isDown) {
    LineRange lineRange = super.getRangeToMove(editor, file, isDown);
    if (!(file instanceof PsiJavaFile)) {
      return null;
    }
    final Pair<PsiElement, PsiElement> psiRange = getElementRange(editor, file, lineRange);
    if (psiRange == null) return null;

    final PsiMember firstMember = PsiTreeUtil.getParentOfType(psiRange.getFirst(), PsiMember.class, false);
    final PsiMember lastMember = PsiTreeUtil.getParentOfType(psiRange.getSecond(), PsiMember.class, false);
    if (firstMember != null && firstMember == lastMember) {
      final LineRange newRange = memberRange(firstMember, editor, lineRange);
      newRange.firstElement = firstMember;
      newRange.lastElement = lastMember;
      return newRange;
    }

    final PsiElement parent = PsiTreeUtil.findCommonParent(firstMember, lastMember);
    if (parent == null) return null;

    final Pair<PsiElement, PsiElement> combinedRange = getElementRange(parent, firstMember, lastMember);
    if (combinedRange == null) return null;
    final LineRange lineRange1 = memberRange(combinedRange.getFirst(), editor, lineRange);
    if (lineRange1 == null) return null;
    final LineRange lineRange2 = memberRange(combinedRange.getSecond(), editor, lineRange);
    if (lineRange2 == null) return null;


    final LineRange newRange = new LineRange(lineRange1.startLine, lineRange2.endLine);
    newRange.firstElement = combinedRange.getFirst();
    newRange.lastElement = combinedRange.getSecond();
    return newRange;
  }

  private static LineRange memberRange(PsiElement member, Editor editor, LineRange lineRange) {
    final TextRange textRange = member.getTextRange();
    // we should be positioned on member start or end to be able to move it
    final int startLine = editor.offsetToLogicalPosition(textRange.getStartOffset()).line;
    final int endLine = editor.offsetToLogicalPosition(textRange.getEndOffset()).line;
    if (startLine != lineRange.startLine && startLine != lineRange.endLine && endLine != lineRange.startLine &&
        endLine != lineRange.endLine) {
      return null;
    }

    return new LineRange(startLine, endLine);
  }

  public int getOffsetToMoveTo(Editor editor, PsiFile file, LineRange range, boolean isDown) {
    return calcInsertOffset(editor, range, isDown);
  }
  private static int calcInsertOffset(Editor editor, LineRange range, final boolean isDown) {
    PsiElement sibling = isDown ? range.lastElement.getNextSibling() : range.firstElement.getPrevSibling();
    if (sibling == null) return -1;
    final boolean areWeMovingClass = range.firstElement instanceof PsiClass;
    sibling = firstNonWhiteElement(sibling, isDown);
    int offset = moveInsideOutsideClassOffset(editor, sibling, isDown, areWeMovingClass);
    if (offset != 0) return offset;
    if (isDown) {
      sibling = sibling.getNextSibling();
      if (sibling == null) return -1;
      sibling = firstNonWhiteElement(sibling, isDown);
      if (sibling == null) return -1;
    }

    return sibling.getTextRange().getStartOffset();
  }

  private static int moveInsideOutsideClassOffset(Editor editor,
                                                  PsiElement sibling,
                                                  final boolean isDown,
                                                  boolean areWeMovingClass) {
    if (sibling == null) return -1;
    if (sibling instanceof PsiJavaToken &&
        ((PsiJavaToken)sibling).getTokenType() == (isDown ? JavaTokenType.RBRACE : JavaTokenType.LBRACE) &&
        sibling.getParent() instanceof PsiClass) {
      // moving outside class
      final PsiClass aClass = (PsiClass)sibling.getParent();
      final PsiElement parent = aClass.getParent();
      if (!areWeMovingClass && !(parent instanceof PsiClass)) return -1;
      return isDown ? nextLineOffset(editor, aClass.getTextRange().getEndOffset()) : aClass.getTextRange().getStartOffset();
    }
    if (sibling instanceof PsiClass) {
      // moving inside class
      return isDown
             ? nextLineOffset(editor, ((PsiClass)sibling).getLBrace().getTextOffset())
             : ((PsiClass)sibling).getRBrace().getTextOffset();
    }
    return 0;
  }

  private static int nextLineOffset(Editor editor, final int offset) {
    final LogicalPosition position = editor.offsetToLogicalPosition(offset);
    return editor.logicalPositionToOffset(new LogicalPosition(position.line + 1, 0));
  }
}
