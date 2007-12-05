package com.intellij.structuralsearch.impl.matcher.filters;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiTypeParameter;

/**
 * Filter for typed symbols
 */
public class TypedSymbolNodeFilter extends NodeFilter {
  @Override public void visitMethod(PsiMethod psiMethod) {
    result = psiMethod.getTypeParameters().length > 0;
  }

  @Override public void visitClass(PsiClass psiClass) {
    result = psiClass.getTypeParameters().length > 0;
  }

  @Override public void visitReferenceElement(PsiJavaCodeReferenceElement psiJavaCodeReferenceElement) {
    result = psiJavaCodeReferenceElement.getParameterList().getTypeParameterElements().length > 0;
  }

  @Override public void visitTypeParameter(PsiTypeParameter parameter) {
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
