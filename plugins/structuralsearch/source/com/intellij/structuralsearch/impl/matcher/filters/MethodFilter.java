package com.intellij.structuralsearch.impl.matcher.filters;

import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReferenceExpression;

/**
 * Filters method nodes
 */
public class MethodFilter extends JavaElementVisitor implements NodeFilter {
  protected boolean result;

  public void visitReferenceExpression(final PsiReferenceExpression expression) {
  }

  @Override public void visitMethod(PsiMethod psiMethod) {
    result = true;
  }

  private MethodFilter() {}

  public static NodeFilter getInstance() {
    if (instance==null) instance = new MethodFilter();
    return instance;
  }
  private static NodeFilter instance;

  public boolean accepts(PsiElement element) {
    result = false;
    if (element!=null) element.accept(this);
    return result;
  }
}
