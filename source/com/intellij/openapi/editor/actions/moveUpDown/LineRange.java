package com.intellij.openapi.editor.actions.moveUpDown;

import com.intellij.psi.PsiElement;

class LineRange {
  final int startLine;
  final int endLine;

  PsiElement firstElement;
  PsiElement lastElement;

  public LineRange(final int startLine, final int endLine) {
    this.startLine = startLine;
    this.endLine = endLine;
  }

  public String toString() {
    return "line range: ["+startLine+"-"+endLine+"]";
  }
}
