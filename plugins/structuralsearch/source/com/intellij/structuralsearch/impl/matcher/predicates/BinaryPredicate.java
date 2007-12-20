package com.intellij.structuralsearch.impl.matcher.predicates;

import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.impl.matcher.handlers.MatchPredicate;
import com.intellij.structuralsearch.impl.matcher.MatchContext;

/**
 * Binary predicate
 */
public final class BinaryPredicate extends MatchPredicate {
  private final MatchPredicate first;
  private final MatchPredicate second;
  private final boolean or;

  public BinaryPredicate(MatchPredicate first, MatchPredicate second, boolean or) {
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

  public MatchPredicate getFirst() {
    return first;
  }

  public MatchPredicate getSecond() {
    return second;
  }
}
