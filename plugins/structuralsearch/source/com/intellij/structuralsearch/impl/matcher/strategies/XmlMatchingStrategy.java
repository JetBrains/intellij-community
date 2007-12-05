package com.intellij.structuralsearch.impl.matcher.strategies;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.xml.XmlTag;
import com.intellij.structuralsearch.impl.matcher.filters.NodeFilter;

/**
 * Base filtering strategy to find statements
 */
public class XmlMatchingStrategy extends NodeFilter implements MatchingStrategy {
  @Override public void visitReferenceExpression(final PsiReferenceExpression psiReferenceExpression) {
    visitExpression(psiReferenceExpression);
  }

  @Override public void visitXmlTag(final XmlTag element) {
    result = true;
  }

  public boolean continueMatching(final PsiElement start) {
    return accepts(start);
  }

  protected XmlMatchingStrategy() {}
  private static XmlMatchingStrategy instance;

  public static MatchingStrategy getInstance() {
    if (instance==null) instance = new XmlMatchingStrategy();
    return instance;
  }
}
