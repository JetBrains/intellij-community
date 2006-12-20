package com.intellij.psi.impl.source.javadoc;

import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.javadoc.PsiDocToken;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.CharTable;

public class PsiDocTokenImpl extends LeafPsiElement implements PsiDocToken{
  public PsiDocTokenImpl(IElementType type, CharSequence buffer, int startOffset, int endOffset, int lexerState, CharTable table) {
    super(type, buffer, startOffset, endOffset, lexerState, table);
  }

  public IElementType getTokenType() {
    return getElementType();
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitDocToken(this);
  }

  public String toString(){
    return "PsiDocToken:" + getTokenType().toString();
  }
}