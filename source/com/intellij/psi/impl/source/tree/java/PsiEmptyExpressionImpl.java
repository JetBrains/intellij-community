package com.intellij.psi.impl.source.tree.java;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import org.jetbrains.annotations.NotNull;

public class PsiEmptyExpressionImpl extends CompositePsiElement implements PsiExpression{
  public PsiEmptyExpressionImpl() {
    super(EMPTY_EXPRESSION);
  }

  public PsiType getType() {
    return null;
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    visitor.visitExpression(this);
  }

  public String toString() {
    return "PsiExpression(empty)";
  }
}
