package com.intellij.structuralsearch.impl.matcher.filters;

import com.intellij.dupLocator.util.NodeFilter;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;

/**
 * Filters method nodes
 */
public class MethodFilter extends JavaElementVisitor implements NodeFilter {
  protected boolean result;

  @Override public void visitMethod(PsiMethod psiMethod) {
    result = true;
  }

  private MethodFilter() {}

  private static class NodeFilterHolder {
    private static final NodeFilter instance = new MethodFilter();
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
