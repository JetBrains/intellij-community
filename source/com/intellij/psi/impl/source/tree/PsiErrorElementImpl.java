
package com.intellij.psi.impl.source.tree;

import com.intellij.psi.*;

public class PsiErrorElementImpl extends CompositePsiElement implements PsiErrorElement{
  private String myErrorDescription;

  public PsiErrorElementImpl() {
    super(ERROR_ELEMENT);
  }

  public void setErrorDescription(String errorDescription) {
    myErrorDescription = errorDescription;
  }

  public String getErrorDescription() {
    return myErrorDescription;
  }

  public void accept(PsiElementVisitor visitor){
    visitor.visitErrorElement(this);
  }

  public String toString(){
    return "PsiErrorElement:" + getErrorDescription();
  }
}