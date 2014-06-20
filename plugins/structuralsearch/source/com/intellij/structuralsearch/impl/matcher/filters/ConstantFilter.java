package com.intellij.structuralsearch.impl.matcher.filters;

import com.intellij.dupLocator.util.NodeFilter;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Apr 27, 2004
 * Time: 7:55:40 PM
 * To change this template use File | Settings | File Templates.
 */
public class ConstantFilter extends JavaElementVisitor implements NodeFilter {
  protected boolean result;

  @Override public void visitLiteralExpression(PsiLiteralExpression  psiLiteral) {
    result = true;
  }

  private static class NodeFilterHolder {
    private static final NodeFilter instance = new ConstantFilter();
  }

  public static NodeFilter getInstance() {
    return NodeFilterHolder.instance;
  }

  private ConstantFilter() {
  }

  public boolean accepts(PsiElement element) {
    result = false;
    if (element!=null) element.accept(this);
    return result;
  }
}
