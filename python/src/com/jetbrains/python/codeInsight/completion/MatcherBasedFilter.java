package com.jetbrains.python.codeInsight.completion;

import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.ElementFilter;
import com.jetbrains.python.psi.patterns.Matcher;

/**
* Filter that relies on a {@link Matcher}.
* User: dcheryasov
* Date: Dec 3, 2009 11:09:21 AM
*/
abstract class MatcherBasedFilter implements ElementFilter {

  abstract Matcher getMatcher();

  public boolean isAcceptable(Object element, PsiElement context) {
    return ((element instanceof PsiElement) && getMatcher().search((PsiElement)element) != null);
  }

  public boolean isClassAcceptable(Class hintClass) {
    return true;
  }
}
