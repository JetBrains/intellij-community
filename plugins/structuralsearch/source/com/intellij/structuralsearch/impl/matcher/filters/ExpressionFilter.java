package com.intellij.structuralsearch.impl.matcher.filters;

import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiExpression;

/**
 * Filters expression nodes
 */
public class ExpressionFilter extends NodeFilter {
  public void visitReferenceExpression(PsiReferenceExpression psiReferenceExpression) {
    result = true;
  }

  public void visitExpression(PsiExpression psiExpression) {
    result = true;
  }

  private static NodeFilter instance;

  public static NodeFilter getInstance() {
    if (instance==null) instance = new ExpressionFilter();
    return instance;
  }

  private ExpressionFilter() {
  }
}
