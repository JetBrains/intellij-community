
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

  public String getExpectedToken() {
    //TODO: hack should be specified when error element is created. Just give it a try
    if (myErrorDescription.indexOf("expected") >= 0) {
      if (myErrorDescription.toLowerCase().indexOf("statement") >= 0) return "{}";
      int startApos = myErrorDescription.indexOf("\'");
      if (startApos >= 0) {
        int stopApos = myErrorDescription.indexOf("\'", startApos + 1);
        if (stopApos >= 0) {
          return myErrorDescription.substring(startApos + 1, stopApos);
        }
      }
    }
    return null;
  }

  public void accept(PsiElementVisitor visitor){
    visitor.visitErrorElement(this);
  }

  public String toString(){
    return "PsiErrorElement:" + getErrorDescription();
  }
}