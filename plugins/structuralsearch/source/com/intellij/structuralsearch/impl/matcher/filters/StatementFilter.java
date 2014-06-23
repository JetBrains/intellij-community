package com.intellij.structuralsearch.impl.matcher.filters;

import com.intellij.dupLocator.util.NodeFilter;
import com.intellij.psi.*;

/**
 * Created by IntelliJ IDEA.
 * User: maxim
 * Date: 26.12.2003
 * Time: 17:46:10
 * To change this template use Options | File Templates.
 */
public class StatementFilter extends JavaElementVisitor implements NodeFilter {
  protected boolean result;

  @Override public void visitReferenceExpression(PsiReferenceExpression psiReferenceExpression) {
    result = false;
  }

  @Override public void visitStatement(PsiStatement psiStatement) {
    result = true;
  }

  @Override public void visitComment(PsiComment comment) {
    result = true;
  }

  public boolean accepts(PsiElement element) {
    result = false;
    if (element!=null) element.accept(this);
    return result;
  }
}
