package com.intellij.structuralsearch.impl.matcher.filters;

import com.intellij.dupLocator.util.NodeFilter;
import com.intellij.psi.*;

/**
 * Filter for typed symbols
 */
public class TypedSymbolNodeFilter extends JavaElementVisitor implements NodeFilter {
  private boolean result;

  @Override public void visitMethod(PsiMethod psiMethod) {
    result = psiMethod.hasTypeParameters();
  }

  @Override public void visitClass(PsiClass psiClass) {
    result = psiClass.hasTypeParameters();
  }

  @Override public void visitReferenceElement(PsiJavaCodeReferenceElement psiJavaCodeReferenceElement) {
    result = psiJavaCodeReferenceElement.getParameterList().getTypeParameterElements().length > 0;
  }

  @Override public void visitTypeParameter(PsiTypeParameter parameter) {
    // we need this since TypeParameter instanceof PsiClass (?)
  }

  private static class NodeFilterHolder {
    private static final NodeFilter instance = new TypedSymbolNodeFilter();
  }

  public static NodeFilter getInstance() {
    return NodeFilterHolder.instance;
  }

  private TypedSymbolNodeFilter() {
  }

  public boolean accepts(PsiElement element) {
    result = false;
    if (element!=null) element.accept(this);
    return result;
  }
}
