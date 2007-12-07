package com.intellij.psi.impl.source.tree.java;

import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiEmptyStatement;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import org.jetbrains.annotations.NotNull;

public class PsiEmptyStatementImpl extends CompositePsiElement implements PsiEmptyStatement {
  public PsiEmptyStatementImpl() {
    super(EMPTY_STATEMENT);
  }

  public void accept(@NotNull PsiElementVisitor visitor){
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitEmptyStatement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString(){
    return "PsiEmptyStatement";
  }
}
