package com.intellij.psi.impl.source.tree;

import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiPlainText;
import com.intellij.util.CharTable;

public class PsiPlainTextImpl extends LeafPsiElement implements PsiPlainText {
  protected PsiPlainTextImpl(char[] buffer, int startOffset, int endOffset, int lexerState, CharTable table) {
    super(PLAIN_TEXT, buffer, startOffset, endOffset, lexerState, table);
  }

  public void accept(PsiElementVisitor visitor){
    visitor.visitPlainText(this);
  }

  public String toString(){
    return "PsiPlainText";
  }
}