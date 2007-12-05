package com.intellij.structuralsearch.impl.matcher.strategies;

import com.intellij.psi.*;

/**
 * Expression matching strategy
 */
public class ExprMatchingStrategy extends MatchingStrategyBase {
  @Override public void visitExpression(final PsiExpression expr) {
    result = true;
  }

  @Override public void visitVariable(final PsiVariable field) {
    result = true;
  }

  @Override public void visitClass(final PsiClass clazz) {
    result = true;
  }

  @Override public void visitClassInitializer(final PsiClassInitializer clazzInit) {
    result = true;
  }

  @Override public void visitMethod(final PsiMethod method) {
    result = true;
  }

  @Override public void visitExpressionList(final PsiExpressionList list) {
    result = true;
  }

  @Override public void visitJavaFile(final PsiJavaFile file) {
    result = true;
  }

  // finding parameters
  @Override public void visitParameterList(final PsiParameterList list) {
    result = true;
  }

  protected ExprMatchingStrategy() {}
  private static ExprMatchingStrategy instance;

  public static MatchingStrategy getInstance() {
    if (instance==null) instance = new ExprMatchingStrategy();
    return instance;
  }
}
