package com.intellij.psi.impl.light;

import com.intellij.psi.*;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.tree.IElementType;

/**
 *
 */
public class LightIdentifier extends LightElement implements PsiIdentifier, PsiJavaToken {
  private String myText;

  public LightIdentifier(PsiManager manager, String text) {
    super(manager);
    myText = text;
  }

  public IElementType getTokenType() {
    return JavaTokenType.IDENTIFIER;
  }

  public String getText(){
    return myText;
  }

  public void accept(PsiElementVisitor visitor){
    visitor.visitIdentifier(this);
  }

  public PsiElement copy(){
    return new LightIdentifier(getManager(), myText);
  }

  public String toString(){
    return "PsiIdentifier:" + getText();
  }
}
