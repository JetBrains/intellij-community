package com.intellij.psi.impl.source.tree;

import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.TokenType;
import com.intellij.util.CharTable;

public class PsiWhiteSpaceImpl extends LeafPsiElement implements PsiWhiteSpace {
  protected PsiWhiteSpaceImpl(char[] buffer, int startOffset, int endOffset, int lexerState, CharTable table) {
    super(TokenType.WHITE_SPACE, buffer, startOffset, endOffset, lexerState, table);
  }

  public void accept(PsiElementVisitor visitor){
    visitor.visitWhiteSpace(this);
  }

  public String toString(){
    return "PsiWhiteSpace";
  }
}
