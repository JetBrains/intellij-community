package com.intellij.structuralsearch.impl.matcher.predicates;

import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.impl.matcher.handlers.Handler;
import com.intellij.structuralsearch.impl.matcher.MatchContext;

/**
 * Binary predicate
 */
public final class BinaryPredicate extends Handler {
  final Handler first;
  final Handler second;
  final boolean or;

  public BinaryPredicate(Handler first, Handler second, boolean or) {
    this.first = first;
    this.second = second;
    this.or = or;
  }

  public boolean match(PsiElement patternNode, PsiElement matchedNode, MatchContext context) {
    if (or) {
      return first.match(patternNode,matchedNode,context) ||
        second.match(patternNode,matchedNode,context);
    } else {
      return first.match(patternNode,matchedNode,context) &&
        second.match(patternNode,matchedNode,context);
    }
  }

  public Handler getFirst() {
    return first;
  }

  public Handler getSecond() {
    return second;
  }
}
