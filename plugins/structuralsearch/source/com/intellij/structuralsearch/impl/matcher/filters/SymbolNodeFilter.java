package com.intellij.structuralsearch.impl.matcher.filters;

import com.intellij.psi.*;

/**
 * Tree filter for searching symbols ('T)
 */
public class SymbolNodeFilter extends NodeFilter {
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

  private static NodeFilter instance;

  public static NodeFilter getInstance() {
    if (instance==null) instance = new SymbolNodeFilter();
    return instance;
  }

  protected SymbolNodeFilter() {
  }
}
