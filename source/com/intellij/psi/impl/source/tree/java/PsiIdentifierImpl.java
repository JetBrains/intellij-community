package com.intellij.psi.impl.source.tree.java;

import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.CharTable;

public class PsiIdentifierImpl extends LeafPsiElement implements PsiIdentifier, PsiJavaToken {
  public PsiIdentifierImpl(CharSequence buffer, int startOffset, int endOffset, CharTable table) {
    super(IDENTIFIER, buffer, startOffset, endOffset, table);
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
