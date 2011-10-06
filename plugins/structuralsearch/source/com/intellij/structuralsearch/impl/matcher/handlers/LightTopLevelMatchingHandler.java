package com.intellij.structuralsearch.impl.matcher.handlers;

import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.impl.matcher.MatchContext;
import com.intellij.dupLocator.iterators.NodeIterator;
import org.jetbrains.annotations.NotNull;

public final class LightTopLevelMatchingHandler extends MatchingHandler implements DelegatingHandler {
  private final MatchingHandler myDelegate;

  public LightTopLevelMatchingHandler(@NotNull MatchingHandler delegate) {
    myDelegate = delegate;
  }

  public boolean match(final PsiElement patternNode, final PsiElement matchedNode, final MatchContext matchContext) {
    return myDelegate.match(patternNode, matchedNode, matchContext);
  }

  @Override
  public boolean canMatch(PsiElement patternNode, PsiElement matchedNode) {
    return myDelegate.canMatch(patternNode, matchedNode);
  }

  @Override
  public boolean matchSequentially(final NodeIterator nodes, final NodeIterator nodes2, final MatchContext context) {
    return myDelegate.matchSequentially(nodes, nodes2, context);
  }

  public boolean match(final PsiElement patternNode,
                       final PsiElement matchedNode, final int start, final int end, final MatchContext context) {
    return myDelegate.match(patternNode, matchedNode, start, end, context);
  }

  public boolean isMatchSequentiallySucceeded(final NodeIterator nodes2) {
    return true;
  }

  @Override
  public boolean shouldAdvanceTheMatchFor(final PsiElement patternElement, final PsiElement matchedElement) {
    return myDelegate.shouldAdvanceTheMatchFor(patternElement, matchedElement);
  }

  public MatchingHandler getDelegate() {
    return myDelegate;
  }
}