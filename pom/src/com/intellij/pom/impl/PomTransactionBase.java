package com.intellij.pom.impl;

import com.intellij.pom.PomTransaction;
import com.intellij.psi.PsiElement;

public abstract class PomTransactionBase implements PomTransaction{
  private PsiElement myScope;
  public PomTransactionBase(PsiElement scope){
    myScope = scope;
  }

  public PsiElement getChangeScope() {
    return myScope;
  }
}
