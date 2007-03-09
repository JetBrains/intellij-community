package com.intellij.psi.impl.source.tree.java;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import org.jetbrains.annotations.NotNull;

public class PsiEmptyStatementImpl extends CompositePsiElement implements PsiEmptyStatement {
  public PsiEmptyStatementImpl() {
    super(EMPTY_STATEMENT);
  }

  public void accept(@NotNull PsiElementVisitor visitor){
    visitor.visitEmptyStatement(this);
  }

  public String toString(){
    return "PsiEmptyStatement";
  }
}
