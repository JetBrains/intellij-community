package com.intellij.codeInsight.template;

import com.intellij.psi.*;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.SmartPointerManager;

public class PsiElementResult implements Result {
  private SmartPsiElementPointer myPointer = null;

  public PsiElementResult(PsiElement element) {
    if (element != null) {
      myPointer = SmartPointerManager.getInstance(element.getProject()).createSmartPsiElementPointer(element);
    }
  }

  public PsiElement getElement() {
    return myPointer != null ? myPointer.getElement() : null;
  }

  public boolean equalsToText(String text, PsiElement context) {
    return text.equals(toString());
  }

  public String toString() {
    String text = null;
    PsiElement element = getElement();
    if (element != null) {
      if (element instanceof PsiVariable) {
        text = ((PsiVariable)element).getName();
      }
      else if (element instanceof PsiMethod) {
        text = ((PsiMethod)element).getName() + "()";
      }
      else if (element instanceof PsiClass) {
        PsiIdentifier identifier = ((PsiClass)element).getNameIdentifier();
        if (identifier == null) return "";
        text = identifier.getText();
      }
      else {
        text = element.getText();
      }
    }
    return text;
  }
}