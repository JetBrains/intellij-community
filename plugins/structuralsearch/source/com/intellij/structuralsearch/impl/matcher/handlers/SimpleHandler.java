package com.intellij.structuralsearch.impl.matcher.handlers;

import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.impl.matcher.iterators.NodeIterator;
import com.intellij.structuralsearch.impl.matcher.MatchContext;
import com.intellij.structuralsearch.impl.matcher.filters.NodeFilter;
import com.intellij.structuralsearch.impl.matcher.handlers.Handler;

/**
 * Root of handlers for pattern node matching. Handles simpliest type of the match.
 */
public final class SimpleHandler extends Handler {
  /**
   * Matches given handler node against given value.
   * @param matchedNode for matching
   * @param context of the matching
   * @return true if matching was successfull and false otherwise
   */
  public boolean match(PsiElement patternNode,PsiElement matchedNode, MatchContext context) {
    if (!super.match(patternNode,matchedNode,context)) return false;
    return context.getMatcher().match(patternNode,matchedNode);
  }
}
