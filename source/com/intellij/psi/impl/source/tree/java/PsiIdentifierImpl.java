package com.intellij.psi.impl.source.tree.java;

import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.CharTable;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.util.CharTable;

public class PsiIdentifierImpl extends LeafPsiElement implements PsiIdentifier, PsiJavaToken {
  public PsiIdentifierImpl(char[] buffer, int startOffset, int endOffset, int lexerState, CharTable table) {
    super(IDENTIFIER, buffer, startOffset, endOffset, lexerState, table);
  }

  public IElementType getTokenType() {
    return JavaTokenType.IDENTIFIER;
  }

  public void accept(PsiElementVisitor visitor){
    visitor.visitIdentifier(this);
  }

  public String toString(){
    return "PsiIdentifier:" + getText();
  }
}
