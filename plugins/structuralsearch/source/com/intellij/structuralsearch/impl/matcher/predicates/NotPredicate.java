package com.intellij.structuralsearch.impl.matcher.predicates;

import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.impl.matcher.handlers.Handler;
import com.intellij.structuralsearch.impl.matcher.MatchContext;

/**
 * Negates predicate
 */
public final class NotPredicate extends Handler {
  private final Handler handler;

  public NotPredicate(final Handler _handler) {
    handler = _handler;
  }

  public boolean match(PsiElement patternNode, PsiElement matchedNode, MatchContext context) {
    return !handler.match(patternNode,matchedNode,context);
  }

  public Handler getHandler() {
    return handler;
  }
}
