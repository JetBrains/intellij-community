package com.intellij.psi.impl.source;

import com.intellij.psi.PsiElement;

public interface PsiElementArrayConstructor<T extends PsiElement> {
  PsiElementArrayConstructor<PsiElement> PSI_ELEMENT_ARRAY_CONSTRUCTOR = new PsiElementArrayConstructor<PsiElement>() {
    public PsiElement[] newPsiElementArray(int length) {
      return length != 0 ? new PsiElement[length] : PsiElement.EMPTY_ARRAY;
    }
  };

  T[] newPsiElementArray(int length);
}
