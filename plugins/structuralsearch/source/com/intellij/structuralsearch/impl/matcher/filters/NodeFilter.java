package com.intellij.structuralsearch.impl.matcher.filters;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiReferenceExpression;

/**
 * Base class for tree filtering
 */
public abstract class NodeFilter extends PsiElementVisitor {
  protected boolean result;
  protected boolean defaultResult = false;

  public boolean accepts(PsiElement element) {
    result = defaultResult;
    if (element!=null) element.accept(this);
    return result;
  }

  @Override public void visitReferenceExpression(PsiReferenceExpression psiReferenceExpression) {
  }
}
