package com.intellij.structuralsearch.impl.matcher.strategies;

import com.intellij.psi.*;

/**
 * Expression matching strategy
 */
public class ExprMatchingStrategy extends MatchingStrategyBase {
  public void visitExpression(final PsiExpression expr) {
    result = true;
  }

  public void visitVariable(final PsiVariable field) {
    result = true;
  }

  public void visitClass(final PsiClass clazz) {
    result = true;
  }

  public void visitClassInitializer(final PsiClassInitializer clazzInit) {
    result = true;
  }

  public void visitMethod(final PsiMethod method) {
    result = true;
  }

  public void visitExpressionList(final PsiExpressionList list) {
    result = true;
  }

  public void visitJavaFile(final PsiJavaFile file) {
    result = true;
  }

  // finding parameters
  public void visitParameterList(final PsiParameterList list) {
    result = true;
  }

  protected ExprMatchingStrategy() {}
  private static ExprMatchingStrategy instance;

  public static MatchingStrategy getInstance() {
    if (instance==null) instance = new ExprMatchingStrategy();
    return instance;
  }
}
