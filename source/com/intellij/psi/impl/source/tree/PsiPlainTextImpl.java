package com.intellij.psi.impl.source.tree;

import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiPlainText;

public class PsiPlainTextImpl extends OwnBufferLeafPsiElement implements PsiPlainText {
  protected PsiPlainTextImpl(char[] buffer, int startOffset, int endOffset, int lexerState) {
    super(PLAIN_TEXT, buffer, startOffset, endOffset, lexerState);
  }

  public void accept(PsiElementVisitor visitor){
    visitor.visitPlainText(this);
  }

  public String toString(){
    return "PsiPlainText";
  }
}