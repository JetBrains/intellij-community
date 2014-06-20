package com.intellij.structuralsearch.impl.matcher.filters;

import com.intellij.dupLocator.util.NodeFilter;
import com.intellij.psi.*;

/**
 * Created by IntelliJ IDEA.
 * User: maxim
 * Date: 26.12.2003
 * Time: 19:23:24
 * To change this template use Options | File Templates.
 */
public class DeclarationFilter extends JavaElementVisitor implements NodeFilter {
  protected boolean result;

  @Override public void visitDeclarationStatement(PsiDeclarationStatement dcl) {
    result = true;
  }

  @Override public void visitVariable(PsiVariable psiVar) {
    result = true;
  }

  @Override public void visitClass(PsiClass psiClass) {
    result = true;
  }

  private static class NodeFilterHolder {
    private static final NodeFilter instance = new DeclarationFilter();
  }

  public static NodeFilter getInstance() {
    return NodeFilterHolder.instance;
  }

  private DeclarationFilter() {
  }

  public boolean accepts(PsiElement element) {
    result = false;
    if (element!=null) element.accept(this);
    return result;
  }
}
