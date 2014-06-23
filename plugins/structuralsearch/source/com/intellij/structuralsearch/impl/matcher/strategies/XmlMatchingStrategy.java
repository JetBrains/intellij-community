package com.intellij.structuralsearch.impl.matcher.strategies;

import com.intellij.dupLocator.util.NodeFilter;
import com.intellij.psi.PsiElement;
import com.intellij.psi.XmlElementVisitor;
import com.intellij.psi.xml.XmlTag;

/**
 * Base filtering strategy to find statements
 */
public class XmlMatchingStrategy extends XmlElementVisitor implements MatchingStrategy,NodeFilter {
  protected boolean result;

  @Override public void visitXmlTag(final XmlTag element) {
    result = true;
  }

  public boolean continueMatching(final PsiElement start) {
    return accepts(start);
  }

  @Override
  public boolean shouldSkip(PsiElement element, PsiElement elementToMatchWith) {
    return false;
  }

  protected XmlMatchingStrategy() {}

  private static class XmlMatchingStrategyHolder {
    private static final XmlMatchingStrategy instance = new XmlMatchingStrategy();
  }

  public static MatchingStrategy getInstance() {
    return XmlMatchingStrategyHolder.instance;
  }

  public boolean accepts(PsiElement element) {
    result = false;
    if (element!=null) element.accept(this);
    return result;
  }
}
