package com.intellij.psi.impl.source.tree.java;

import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.CharTable;

public class PsiJavaTokenImpl extends LeafPsiElement implements PsiJavaToken, JavaTokenType{
  public PsiJavaTokenImpl(IElementType type, CharSequence buffer, int startOffset, int endOffset, int lexerState, CharTable table) {
    super(type, buffer, startOffset, endOffset, lexerState, table);
  }

  public IElementType getTokenType() {
    return getElementType();
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitJavaToken(this);
  }

  public String toString(){
    return "PsiJavaToken:" + getElementType().toString();
  }
}