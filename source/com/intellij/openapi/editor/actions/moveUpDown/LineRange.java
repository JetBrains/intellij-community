package com.intellij.openapi.editor.actions.moveUpDown;

import com.intellij.psi.PsiElement;
import com.intellij.openapi.editor.Document;
import org.jetbrains.annotations.NonNls;

class LineRange {
  final int startLine;
  final int endLine;

  PsiElement firstElement;
  PsiElement lastElement;

  public LineRange(final int startLine, final int endLine) {
    this.startLine = startLine;
    this.endLine = endLine;
  }
  public LineRange(PsiElement startElement, PsiElement endElement, Document document) {
    startLine = document.getLineNumber(startElement.getTextRange().getStartOffset());
    endLine = document.getLineNumber(endElement.getTextRange().getEndOffset()) +1;
  }

  @NonNls
  public String toString() {
    return "line range: ["+startLine+"-"+endLine+"]";
  }
}
