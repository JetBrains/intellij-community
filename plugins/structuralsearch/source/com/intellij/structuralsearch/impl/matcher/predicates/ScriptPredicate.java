package com.intellij.structuralsearch.impl.matcher.predicates;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.structuralsearch.MatchResult;
import com.intellij.structuralsearch.impl.matcher.MatchContext;
import com.intellij.structuralsearch.impl.matcher.MatchResultImpl;
import groovy.lang.GroovyShell;

/**
 * @author Maxim.Mossienko
 */
public class ScriptPredicate extends AbstractStringBasedPredicate {
  private final GroovyShell groovyShell;

  public ScriptPredicate(String name, String within) {
    super(name, within);
    groovyShell = new GroovyShell();
  }

  public boolean match(PsiElement node, PsiElement match, int start, int end, MatchContext context) {
    if (match == null) return false;
    groovyShell.initializeBinding();

    if (context.hasResult()) {
      final MatchResultImpl result = context.getResult();
      for(MatchResult r:result.getMatches()) {
        groovyShell.setVariable(r.getName(),r.getMatchRef().getElement());
      }
    }

    if (match instanceof PsiIdentifier) match = match.getParent();
    groovyShell.setVariable("__context__",match);

    final Object value = groovyShell.evaluate(StringUtil.stripQuotesAroundValue(myWithin));
    return Boolean.TRUE.equals(value);
  }
}