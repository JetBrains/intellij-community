package com.intellij.structuralsearch.impl.matcher.filters;

import com.intellij.dupLocator.util.NodeFilter;
import com.intellij.psi.*;

/**
 * Created by IntelliJ IDEA.
 * User: maxim
 * Date: 03.01.2004
 * Time: 1:06:23
 * To change this template use Options | File Templates.
 */
public class TypeParameterFilter extends JavaElementVisitor implements NodeFilter {
  protected boolean result;

  @Override public void visitTypeElement(PsiTypeElement psiTypeElement) {
    result = true;
  }

  @Override public void visitTypeParameter(PsiTypeParameter psiTypeParameter) {
    result = true;
  }

  @Override public void visitReferenceElement(PsiJavaCodeReferenceElement psiJavaCodeReferenceElement) {
    result = true;
  }

  private TypeParameterFilter() {}

  private static class NodeFilterHolder {
    private static final NodeFilter instance = new TypeParameterFilter();
  }

  public static NodeFilter getInstance() {
    return NodeFilterHolder.instance;
  }

  public boolean accepts(PsiElement element) {
    result = false;
    if (element!=null) element.accept(this);
    return result;
  }
}
