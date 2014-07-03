package com.intellij.structuralsearch.impl.matcher.strategies;

import com.intellij.dupLocator.util.NodeFilter;
import com.intellij.psi.*;

/**
 * Base filtering strategy to find statements
 */
public class MatchingStrategyBase extends JavaElementVisitor implements MatchingStrategy, NodeFilter {
  protected boolean result;

  @Override public void visitReferenceExpression(final PsiReferenceExpression psiReferenceExpression) {
    visitExpression(psiReferenceExpression);
  }

  @Override public void visitCodeBlock(final PsiCodeBlock block) {
    result = true;
  }
  
  @Override public void visitCatchSection(final PsiCatchSection section) {
    result = true;
  }

  @Override public void visitStatement(final PsiStatement statement) {
    result = true;
  }

  public boolean continueMatching(final PsiElement start) {
    return accepts(start);
  }

  @Override
  public boolean shouldSkip(PsiElement element, PsiElement elementToMatchWith) {
    return false;
  }

  public boolean accepts(PsiElement element) {
    result = false;
    if (element!=null) element.accept(this);
    return result;
  }
}
