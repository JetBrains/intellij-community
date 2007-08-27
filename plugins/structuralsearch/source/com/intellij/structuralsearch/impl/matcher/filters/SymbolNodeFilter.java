package com.intellij.structuralsearch.impl.matcher.filters;

import com.intellij.psi.*;

/**
 * Tree filter for searching symbols ('T)
 */
public class SymbolNodeFilter extends NodeFilter {
  public void visitLiteralExpression(PsiLiteralExpression psiLiteralExpression) {
    result = true;
  }

  public void visitReferenceExpression(PsiReferenceExpression psiReferenceExpression) {
    result = true;
  }

  public void visitAnnotation(final PsiAnnotation annotation) {
    result = true;
  }

  public void visitAnnotationMethod(final PsiAnnotationMethod method) {
    result = true;
  }

  public void visitNameValuePair(final PsiNameValuePair pair) {
    result = true;
  }

  public void visitMethod(PsiMethod psiMethod) {
    result = true;
  }

  public void visitClass(PsiClass psiClass) {
    result = true;
  }

  public void visitReferenceElement(PsiJavaCodeReferenceElement psiJavaCodeReferenceElement) {
    result = true;
  }

  public void visitVariable(PsiVariable psiVar) {
    result = true;
  }

  public void visitTypeParameter(PsiTypeParameter psiTypeParameter) {
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
