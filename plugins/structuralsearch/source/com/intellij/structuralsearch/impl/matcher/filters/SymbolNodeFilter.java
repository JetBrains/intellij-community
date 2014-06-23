package com.intellij.structuralsearch.impl.matcher.filters;

import com.intellij.dupLocator.util.NodeFilter;
import com.intellij.psi.*;

/**
 * Tree filter for searching symbols ('T)
 */
public class SymbolNodeFilter extends JavaElementVisitor implements NodeFilter {
  private boolean result;

  @Override public void visitExpression(PsiExpression expr) {
    result = true;
  }

  @Override public void visitLiteralExpression(PsiLiteralExpression psiLiteralExpression) {
    result = true;
  }

  @Override public void visitReferenceExpression(PsiReferenceExpression psiReferenceExpression) {
    result = true;
  }

  @Override public void visitAnnotation(final PsiAnnotation annotation) {
    result = true;
  }

  @Override public void visitAnnotationMethod(final PsiAnnotationMethod method) {
    result = true;
  }

  @Override public void visitNameValuePair(final PsiNameValuePair pair) {
    result = true;
  }

  @Override public void visitMethod(PsiMethod psiMethod) {
    result = true;
  }

  @Override public void visitClass(PsiClass psiClass) {
    result = true;
  }

  @Override public void visitReferenceElement(PsiJavaCodeReferenceElement psiJavaCodeReferenceElement) {
    result = true;
  }

  @Override public void visitVariable(PsiVariable psiVar) {
    result = true;
  }

  @Override public void visitTypeParameter(PsiTypeParameter psiTypeParameter) {
    result = true;
  }

  private static class NodeFilterHolder {
    private static final NodeFilter instance = new SymbolNodeFilter();
  }

  public static NodeFilter getInstance() {
    return NodeFilterHolder.instance;
  }

  private SymbolNodeFilter() {
  }

  public boolean accepts(PsiElement element) {
    result = false;
    if (element!=null) element.accept(this);
    return result;
  }
}
