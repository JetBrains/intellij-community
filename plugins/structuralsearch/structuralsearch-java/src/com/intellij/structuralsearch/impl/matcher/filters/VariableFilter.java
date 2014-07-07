package com.intellij.structuralsearch.impl.matcher.filters;

import com.intellij.dupLocator.util.NodeFilter;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiVariable;

/**
 * Created by IntelliJ IDEA.
 * User: maxim
 * Date: 26.12.2003
 * Time: 19:52:57
 * To change this template use Options | File Templates.
 */
public class VariableFilter extends JavaElementVisitor implements NodeFilter {
  protected boolean result;

  public void visitReferenceExpression(final PsiReferenceExpression expression) {
  }

  @Override public void visitVariable(PsiVariable psiVariable) {
    result = true;
  }

  private VariableFilter() {}

  private static class NodeFilterHolder {
    private static final NodeFilter instance = new VariableFilter();
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
