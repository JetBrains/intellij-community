package com.intellij.structuralsearch.impl.matcher.filters;

import com.intellij.psi.PsiLiteralExpression;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Apr 27, 2004
 * Time: 7:55:40 PM
 * To change this template use File | Settings | File Templates.
 */
public class ConstantFilter extends NodeFilter {
  @Override public void visitLiteralExpression(PsiLiteralExpression  psiLiteral) {
    result = true;
  }

  private static NodeFilter instance;

  public static NodeFilter getInstance() {
    if (instance==null) instance = new ConstantFilter();
    return instance;
  }

  private ConstantFilter() {
  }
}
