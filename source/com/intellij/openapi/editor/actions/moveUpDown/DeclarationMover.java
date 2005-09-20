package com.intellij.openapi.editor.actions.moveUpDown;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;

class DeclarationMover extends LineMover {
  public InsertionInfo getInsertionInfo(Editor editor, PsiFile file, boolean isDown) {
    if (!(file instanceof PsiJavaFile)) {
      return null;
    }
    final InsertionInfo oldInsertionInfo = super.getInsertionInfo(editor, file, isDown);
    if (oldInsertionInfo == null) return null;
    LineRange oldRange = oldInsertionInfo.whatToMove;
    final Pair<PsiElement, PsiElement> psiRange = getElementRange(editor, file, oldRange);
    if (psiRange == null) return null;

    final PsiMember firstMember = PsiTreeUtil.getParentOfType(psiRange.getFirst(), PsiMember.class, false);
    final PsiMember lastMember = PsiTreeUtil.getParentOfType(psiRange.getSecond(), PsiMember.class, false);
    LineRange range;
    if (firstMember != null && firstMember == lastMember) {
      range = memberRange(firstMember, editor, oldRange);
      if (range == null) return null;
      range.firstElement = firstMember;
      range.lastElement = lastMember;
    }
    else {
      if (firstMember == null || lastMember == null) return null;
      final PsiElement parent = PsiTreeUtil.findCommonParent(firstMember, lastMember);
      if (parent == null) return null;

      final Pair<PsiElement, PsiElement> combinedRange = getElementRange(parent, firstMember, lastMember);
      if (combinedRange == null) return null;
      final LineRange lineRange1 = memberRange(combinedRange.getFirst(), editor, oldRange);
      if (lineRange1 == null) return null;
      final LineRange lineRange2 = memberRange(combinedRange.getSecond(), editor, oldRange);
      if (lineRange2 == null) return null;

      range = new LineRange(lineRange1.startLine, lineRange2.endLine);
      range.firstElement = combinedRange.getFirst();
      range.lastElement = combinedRange.getSecond();
    }
    PsiElement nextWhitespace = range.lastElement.getNextSibling();
    if (nextWhitespace instanceof PsiWhiteSpace) {
      int endLine = editor.offsetToLogicalPosition(nextWhitespace.getTextRange().getEndOffset()).line;
      Document document = editor.getDocument();
      while (true) {
        int lineStartOffset = document.getLineStartOffset(endLine);
        int lineEndOffset = document.getLineEndOffset(endLine);
        PsiElement elementAtStart = file.findElementAt(lineStartOffset);
        PsiElement elementAtEnd = file.findElementAt(lineEndOffset - 1);
        if (elementAtEnd == nextWhitespace && elementAtStart == nextWhitespace) break;
        endLine--;
        if (endLine == range.endLine) break;
      }
      LineRange newRange = new LineRange(range.startLine, endLine);
      newRange.firstElement = range.firstElement;
      newRange.lastElement = nextWhitespace;
      range = newRange;
    }
    return calcInsertOffset(editor, range, isDown);
  }

  private static LineRange memberRange(PsiElement member, Editor editor, LineRange lineRange) {
    final TextRange textRange = member.getTextRange();
    if (editor.getDocument().getTextLength() < textRange.getEndOffset()) return null;
    // we should be positioned on member start or end to be able to move it
    final int startLine = editor.offsetToLogicalPosition(textRange.getStartOffset()).line;
    final int endLine = editor.offsetToLogicalPosition(textRange.getEndOffset()).line;
    if (startLine != lineRange.startLine && startLine != lineRange.endLine && endLine != lineRange.startLine &&
        endLine != lineRange.endLine) {
      return null;
    }

    return new LineRange(startLine, endLine);
  }
  private static InsertionInfo calcInsertOffset(Editor editor, LineRange range, final boolean isDown) {
    PsiElement sibling = isDown ? range.lastElement.getNextSibling() : range.firstElement.getPrevSibling();
    if (sibling == null) return null;
    final boolean areWeMovingClass = range.firstElement instanceof PsiClass;
    sibling = firstNonWhiteElement(sibling, isDown);
    int offset = moveInsideOutsideClassOffset(editor, sibling, isDown, areWeMovingClass);
    if (offset == -1) return InsertionInfo.ILLEGAL_INFO;
    if (offset != 0) return new InsertionInfo(range, offset);
    if (isDown) {
      sibling = sibling.getNextSibling();
      if (sibling == null) return null;
      sibling = firstNonWhiteElement(sibling, isDown);
      if (sibling == null) return null;
    }

    return new InsertionInfo(range, sibling.getTextRange().getStartOffset());
  }

  // 0 means we are not moving in/out class
  // -1 means illegal move in/out class
  // other offset - specific offset inside/outside class
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
