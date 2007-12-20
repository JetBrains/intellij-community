package com.intellij.structuralsearch.impl.matcher.predicates;

import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.impl.matcher.MatchContext;
import com.intellij.structuralsearch.impl.matcher.handlers.MatchPredicate;

/**
 * @author Maxim.Mossienko
 */
public class AbstractStringBasedPredicate extends MatchPredicate {
  protected final String myName;
  protected final String myWithin;

  public AbstractStringBasedPredicate(String name, String within) {
    myName = name;
    myWithin = within;
  }

  public boolean match(PsiElement patternNode, PsiElement matchedNode, MatchContext context) {
    return match(patternNode, matchedNode, 0, -1, context);
  }
}