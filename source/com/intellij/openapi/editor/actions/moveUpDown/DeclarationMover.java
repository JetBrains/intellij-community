package com.intellij.openapi.editor.actions.moveUpDown;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.lang.StdLanguages;

class DeclarationMover extends LineMover {
  public DeclarationMover(final boolean isDown) {
    super(isDown);
  }

  protected void afterMove(final Editor editor, final PsiFile file) {
    super.afterMove(editor, file);
    final int line1 = editor.offsetToLogicalPosition(myInsertStartAfterCut).line;
    final int line2 = editor.offsetToLogicalPosition(myInsertEndAfterCut).line;
    PsiElement above;
    PsiElement hereStart;
    PsiElement hereEnd;
    PsiElement below;
    if (myIsDown) {
      above = findMemberOnLineEnd(line1-1, editor, file);
      hereStart = findMemberOnLineStart(line1, editor, file);
      hereEnd = findMemberOnLineEnd(line1, editor, file);
      below = findMemberOnLineStart(line2, editor, file);
    }
    else {
      above = findMemberOnLineEnd(line1, editor, file);
      hereStart = findMemberOnLineStart(line2, editor, file);
      hereEnd = findMemberOnLineEnd(line2, editor, file);
      below = hereEnd == null? null : hereEnd.getNextSibling();
      if (below instanceof PsiWhiteSpace) below = below.getNextSibling();
    }
    fixupWhiteSpaceBetween(above, hereStart);
    fixupWhiteSpaceBetween(hereEnd, below);
  }

  private void fixupWhiteSpaceBetween(final PsiElement element1, final PsiElement element2) {
    if (element1 != null && element2 != null && element1.getNextSibling() instanceof PsiWhiteSpace && element1.getNextSibling().getNextSibling() == element2) {
      String whiteSpace = CodeEditUtil.getStringWhiteSpaceBetweenTokens(element1.getNode(), element2.getNode(), StdLanguages.JAVA);
      LeafElement ws = Factory.createSingleLeafElement(TokenType.WHITE_SPACE, whiteSpace.toCharArray(), 0, whiteSpace.length(), SharedImplUtil.findCharTableByTree(element1.getNode()), element1.getManager());
      element1.getNextSibling().getParent().getNode().replaceChild(element1.getNextSibling().getNode(), ws);
      PsiDocumentManager documentManager = PsiDocumentManager.getInstance(element1.getProject());
      documentManager.commitDocument(documentManager.getDocument(element1.getContainingFile()));
    }
  }

  private static PsiElement findMemberOnLineStart(final int line, final Editor editor, final PsiFile file) {
    int lineStartOffset = editor.getDocument().getLineStartOffset(line);
    PsiElement element = firstNonWhiteElement(lineStartOffset, file, true);
    return element;
  }
  private static PsiElement findMemberOnLineEnd(final int line, final Editor editor, final PsiFile file) {
    int lineStartOffset = editor.getDocument().getLineEndOffset(line);
    PsiElement element = firstNonWhiteElement(lineStartOffset, file, false);
    return element;
  }

  protected boolean checkAvailable(Editor editor, PsiFile file) {
    if (!(file instanceof PsiJavaFile)) {
      return false;
    }
    boolean available = super.checkAvailable(editor, file);
    if (!available) return false;
    LineRange oldRange = whatToMove;
    final Pair<PsiElement, PsiElement> psiRange = getElementRange(editor, file, oldRange);
    if (psiRange == null) return false;

    final PsiMember firstMember = PsiTreeUtil.getParentOfType(psiRange.getFirst(), PsiMember.class, false);
    final PsiMember lastMember = PsiTreeUtil.getParentOfType(psiRange.getSecond(), PsiMember.class, false);
    if (firstMember == null || lastMember == null) return false;
    LineRange range;
    if (firstMember == lastMember) {
      range = memberRange(firstMember, editor, oldRange);
      if (range == null) return false;
      range.firstElement = range.lastElement = firstMember;
    }
    else {
      final PsiElement parent = PsiTreeUtil.findCommonParent(firstMember, lastMember);
      if (parent == null) return false;

      final Pair<PsiElement, PsiElement> combinedRange = getElementRange(parent, firstMember, lastMember);
      if (combinedRange == null) return false;
      final LineRange lineRange1 = memberRange(combinedRange.getFirst(), editor, oldRange);
      if (lineRange1 == null) return false;
      final LineRange lineRange2 = memberRange(combinedRange.getSecond(), editor, oldRange);
      if (lineRange2 == null) return false;

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


    PsiElement sibling = myIsDown ? range.lastElement.getNextSibling() : range.firstElement.getPrevSibling();
    if (sibling == null) return false;
    final boolean areWeMovingClass = range.firstElement instanceof PsiClass;
    sibling = firstNonWhiteElement(sibling, myIsDown);
    int offset = moveInsideOutsideClassOffset(editor, sibling, myIsDown, areWeMovingClass);
    if (offset == ILLEGAL_MOVE) {
      insertOffset = -1;
      return true;
    }
    if (offset != NOT_CROSSING_CLASS_BORDER) {
      whatToMove = range;
      insertOffset = offset;
      return true;
    }
    if (myIsDown) {
      sibling = sibling.getNextSibling();
      if (sibling == null) return false;
      sibling = firstNonWhiteElement(sibling, myIsDown);
      if (sibling == null) return false;
    }

    whatToMove = range;
    insertOffset = sibling.getTextRange().getStartOffset();
    return true;
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

  private static final int ILLEGAL_MOVE = -1;
  private static final int NOT_CROSSING_CLASS_BORDER = -2;
  private static int moveInsideOutsideClassOffset(Editor editor,
                                                  PsiElement sibling,
                                                  final boolean isDown,
                                                  boolean areWeMovingClass) {
    if (sibling == null) return ILLEGAL_MOVE;
    if (sibling instanceof PsiJavaToken &&
        ((PsiJavaToken)sibling).getTokenType() == (isDown ? JavaTokenType.RBRACE : JavaTokenType.LBRACE) &&
        sibling.getParent() instanceof PsiClass) {
      // moving outside class
      final PsiClass aClass = (PsiClass)sibling.getParent();
      final PsiElement parent = aClass.getParent();
      if (!areWeMovingClass && !(parent instanceof PsiClass)) return ILLEGAL_MOVE;
      return isDown ? nextLineOffset(editor, aClass.getTextRange().getEndOffset()) : aClass.getTextRange().getStartOffset();
    }
    if (sibling instanceof PsiClass) {
      // moving inside class
      return isDown
             ? nextLineOffset(editor, ((PsiClass)sibling).getLBrace().getTextOffset())
             : ((PsiClass)sibling).getRBrace().getTextOffset();
    }
    return NOT_CROSSING_CLASS_BORDER;
  }

  private static int nextLineOffset(Editor editor, final int offset) {
    final LogicalPosition position = editor.offsetToLogicalPosition(offset);
    return editor.logicalPositionToOffset(new LogicalPosition(position.line + 1, 0));
  }
}
