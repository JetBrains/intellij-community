package com.intellij.structuralsearch.impl.matcher.filters;

import com.intellij.psi.*;

/**
 * Filter for typed symbols
 */
public class TypedSymbolNodeFilter extends NodeFilter {
  public void visitMethod(PsiMethod psiMethod) {
    result = psiMethod.getTypeParameters().length > 0;
  }

  public void visitClass(PsiClass psiClass) {
    result = psiClass.getTypeParameters().length > 0;
  }

  public void visitReferenceElement(PsiJavaCodeReferenceElement psiJavaCodeReferenceElement) {
    result = psiJavaCodeReferenceElement.getParameterList().getTypeParameterElements().length > 0;
  }

  public void visitTypeParameter(PsiTypeParameter parameter) {
    // we need this since TypeParameter instanceof PsiClass (?)
  }

  private static NodeFilter instance;

  public static NodeFilter getInstance() {
    if (instance==null) instance = new TypedSymbolNodeFilter();
    return instance;
  }

  private TypedSymbolNodeFilter() {
  }
}
