package com.intellij.psi.impl.source.tree.java;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.CompositePsiElement;

public class PsiEmptyStatementImpl extends CompositePsiElement implements PsiEmptyStatement {
  public PsiEmptyStatementImpl() {
    super(EMPTY_STATEMENT);
  }

  public void accept(PsiElementVisitor visitor){
    visitor.visitEmptyStatement(this);
  }

  public String toString(){
    return "PsiEmptyStatement";
  }
}
