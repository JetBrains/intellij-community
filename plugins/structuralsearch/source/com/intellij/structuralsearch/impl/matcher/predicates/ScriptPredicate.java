package com.intellij.structuralsearch.impl.matcher.predicates;

import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.impl.matcher.MatchContext;

/**
 * @author Maxim.Mossienko
 */
public class ScriptPredicate extends AbstractStringBasedPredicate {

  public ScriptPredicate(String name, String within) {
    super(name, within);
  }

  public boolean match(PsiElement node, PsiElement match, int start, int end, MatchContext context) {
    return false;
  }
}