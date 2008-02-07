package com.intellij.psi.impl.light;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import org.jetbrains.annotations.NotNull;

/**
 *
 */
public class LightIdentifier extends LightElement implements PsiIdentifier, PsiJavaToken {
  private String myText;

  public LightIdentifier(PsiManager manager, String text) {
    super(manager, StdFileTypes.JAVA.getLanguage());
    myText = text;
  }

  public IElementType getTokenType() {
    return JavaTokenType.IDENTIFIER;
  }

  public String getText(){
    return myText;
  }

  public void accept(@NotNull PsiElementVisitor visitor){
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitIdentifier(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public PsiElement copy(){
    return new LightIdentifier(getManager(), myText);
  }

  public String toString(){
    return "PsiIdentifier:" + getText();
  }
}
