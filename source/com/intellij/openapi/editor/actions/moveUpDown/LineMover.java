package com.intellij.openapi.editor.actions.moveUpDown;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;

class LineMover extends Mover {
  public LineMover(final boolean isDown) {
    super(isDown);
  }

  protected boolean checkAvailable(Editor editor, PsiFile file) {
    final SelectionModel selectionModel = editor.getSelectionModel();
    final int startLine;
    final int endLine;
    LineRange range;
    if (selectionModel.hasSelection()) {
      startLine = editor.offsetToLogicalPosition(selectionModel.getSelectionStart()).line;
      final LogicalPosition endPos = editor.offsetToLogicalPosition(selectionModel.getSelectionEnd());
      endLine = endPos.column == 0 ? endPos.line : endPos.line+1;
      range = new LineRange(startLine, endLine);
    }
    else {
      startLine = editor.getCaretModel().getLogicalPosition().line;
      endLine = startLine+1;
      range = new LineRange(startLine, endLine);
    }

    final int maxLine = editor.offsetToLogicalPosition(editor.getDocument().getTextLength()).line;
    if (range.startLine <= 1 && !isDown) return false;
    if (range.endLine >= maxLine && isDown) return false;

    toMove = range;
    int nearLine = isDown ? range.endLine : range.startLine - 1;
    toMove2 = new LineRange(nearLine, nearLine+1);
    //insertOffset = editor.logicalPositionToOffset(new LogicalPosition(nearLine, 0));
    return true;
  }

  protected static Pair<PsiElement, PsiElement> getElementRange(Editor editor, PsiFile file, final LineRange range) {
    final int startOffset = editor.logicalPositionToOffset(new LogicalPosition(range.startLine, 0));
    PsiElement startingElement = firstNonWhiteElement(startOffset, file, true);
    if (startingElement == null) return null;
    final int endOffset = editor.logicalPositionToOffset(new LogicalPosition(range.endLine, 0)) -1;

    PsiElement endingElement = firstNonWhiteElement(endOffset, file, false);
    if (endingElement == null) return null;
    if (!PsiTreeUtil.isAncestor(startingElement, endingElement, false)
        && startingElement.getTextRange().getEndOffset() > endingElement.getTextRange().getStartOffset()) return null;
    return Pair.create(startingElement, endingElement);
  }

  static PsiElement firstNonWhiteElement(int offset, PsiFile file, final boolean lookRight) {
    final ASTNode leafElement = file.getNode().findLeafElementAt(offset);
    return leafElement == null ? null : firstNonWhiteElement(leafElement.getPsi(), lookRight);
  }

  static PsiElement firstNonWhiteElement(PsiElement element, final boolean lookRight) {
    if (element instanceof PsiWhiteSpace) {
      element = lookRight ? element.getNextSibling() : element.getPrevSibling();
    }
    return element;
  }

  protected static Pair<PsiElement, PsiElement> getElementRange(final PsiElement parent,
                                                                PsiElement element1,
                                                                PsiElement element2) {
    if (PsiTreeUtil.isAncestor(element1, element2, false) || PsiTreeUtil.isAncestor(element2, element1, false)) {
      return Pair.create(parent, parent);
    }
    // find nearset children that are parents of elements
    while (element1 != null && element1.getParent() != parent) {
      element1 = element1.getParent();
    }
    while (element2 != null && element2.getParent() != parent) {
      element2 = element2.getParent();
    }
    if (element1 == null || element2 == null) return null;
    if (element1 != element2) {
      assert element1.getTextRange().getEndOffset() <= element2.getTextRange().getStartOffset() : element1.getTextRange() + "-"+element2.getTextRange()+element1+element2;
    }
    return Pair.create(element1, element2);
  }
}
