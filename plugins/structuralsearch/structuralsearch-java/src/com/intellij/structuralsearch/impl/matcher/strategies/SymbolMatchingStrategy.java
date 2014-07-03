package com.intellij.structuralsearch.impl.matcher.strategies;

import com.intellij.psi.*;

/**
 * CommonStrategy to match symbols
 */
public class SymbolMatchingStrategy extends ExprMatchingStrategy {
  @Override public void visitReferenceList(final PsiReferenceList list) {
    result = true;
  }

  @Override public void visitAnnotation(final PsiAnnotation annotation) {
    result = true;
  }

  @Override public void visitAnnotationParameterList(final PsiAnnotationParameterList list) {
    result = true;
  }

  @Override public void visitModifierList(final PsiModifierList list) {
    result = true;
  }

  @Override public void visitNameValuePair(final PsiNameValuePair pair) {
    result = true;
  }

  @Override public void visitTypeParameterList(PsiTypeParameterList psiTypeParameterList) {
    result = true;
  }

  @Override public void visitTypeElement(PsiTypeElement psiTypeElement) {
    result = true;
  }

  @Override public void visitReferenceElement(PsiJavaCodeReferenceElement psiJavaCodeReferenceElement) {
    result = true;
  }

  @Override public void visitReferenceParameterList(PsiReferenceParameterList psiReferenceParameterList) {
    result = true;
  }

  private SymbolMatchingStrategy() {}

  private static class SymbolMatchingStrategyHolder {
    private static final SymbolMatchingStrategy instance = new SymbolMatchingStrategy();
  }

  public static MatchingStrategy getInstance() {
    return SymbolMatchingStrategyHolder.instance;
  }
}
