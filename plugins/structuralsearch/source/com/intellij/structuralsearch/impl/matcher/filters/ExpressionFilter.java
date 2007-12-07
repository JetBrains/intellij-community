package com.intellij.structuralsearch.impl.matcher.filters;

import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiReferenceExpression;

/**
 * Filters expression nodes
 */
public class ExpressionFilter extends JavaElementVisitor implements NodeFilter {
  protected boolean result;

  @Override public void visitReferenceExpression(PsiReferenceExpression psiReferenceExpression) {
    result = true;
  }

  @Override public void visitExpression(PsiExpression psiExpression) {
    result = true;
  }

  private static NodeFilter instance;

  public static NodeFilter getInstance() {
    if (instance==null) instance = new ExpressionFilter();
    return instance;
  }

  private ExpressionFilter() {
  }

  public boolean accepts(PsiElement element) {
    result = false;
    if (element!=null) element.accept(this);
    return result;
  }
}
