package com.intellij.structuralsearch.impl.matcher.filters;

import com.intellij.psi.PsiMethod;

/**
 * Filters method nodes
 */
public class MethodFilter extends NodeFilter {
  @Override public void visitMethod(PsiMethod psiMethod) {
    result = true;
  }

  private MethodFilter() {}

  public static NodeFilter getInstance() {
    if (instance==null) instance = new MethodFilter();
    return instance;
  }
  private static NodeFilter instance;
}
