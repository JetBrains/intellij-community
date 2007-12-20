package com.intellij.structuralsearch.impl.matcher.predicates;

import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.impl.matcher.MatchContext;
import com.intellij.structuralsearch.impl.matcher.handlers.MatchPredicate;

/**
 * Negates predicate
 */
public final class NotPredicate extends MatchPredicate {
  private final MatchPredicate handler;

  public NotPredicate(final MatchPredicate _handler) {
    handler = _handler;
  }

  public boolean match(PsiElement patternNode, PsiElement matchedNode, MatchContext context) {
    return !handler.match(patternNode,matchedNode,context);
  }

  public MatchPredicate getHandler() {
    return handler;
  }
}
