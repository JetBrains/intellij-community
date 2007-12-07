package com.intellij.structuralsearch.impl.matcher.filters;

import com.intellij.psi.*;

/**
 * Created by IntelliJ IDEA.
 * User: maxim
 * Date: 23.01.2004
 * Time: 1:07:09
 * To change this template use File | Settings | File Templates.
 */
public class TypeFilter extends JavaElementVisitor implements NodeFilter {
  protected boolean result;

  public void visitReferenceExpression(final PsiReferenceExpression expression) {
  }

  @Override public void visitClass(PsiClass aClass) {
    result = true;
  }

  @Override public void visitReferenceElement(PsiJavaCodeReferenceElement psiMethod) {
    result = true;
  }

  private TypeFilter() {}

  public static NodeFilter getInstance() {
    if (instance==null) instance = new TypeFilter();
    return instance;
  }
  private static NodeFilter instance;

  public boolean accepts(PsiElement element) {
    result = false;
    if (element!=null) element.accept(this);
    return result;
  }
}
