package com.jetbrains.python.codeInsight.completion;

import com.intellij.openapi.util.UserDataHolder;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.codeInsight.PySeeingOriginalCompletionContributor;
import org.jetbrains.annotations.NotNull;

/**
 * Ported from TreeElementPattern.insideSequence for the sake of extraction of non-fake ctrl+space element.
 */
class InSequenceFilter implements ElementFilter {
  ElementPattern<? extends PsiElement>[] myPatterns;

  /**
   * Matches if a given sequence of patterns match each on certain parents of the element, in the order given. The match may start well
   * above the element, and the matching elements may come with gaps. But once the first pattern has matched, it will not
   * be reconsidered if the rest did not match.
   * The search will not continue above PsiFile level.
   * @param patterns to match; first pattern is for the deepest element, last is for the outermost.
   */
  public InSequenceFilter(@NotNull final ElementPattern<? extends PsiElement>... patterns) {
    myPatterns = patterns;
  }

  public boolean isAcceptable(Object what, PsiElement context) {
    if (!(what instanceof UserDataHolder)) return false; // can't dream to match
    if (myPatterns.length <= 0) return false; // sanity check
    int patIndex = 0;
    PsiElement true_elt = ((UserDataHolder)what).getUserData(PySeeingOriginalCompletionContributor.ORG_ELT);
    if (true_elt == null) return false; // we're not from here
    PsiElement element = true_elt;
    ProcessingContext ctx = new ProcessingContext();
    // climb until first condition matches
    while (element != null && !myPatterns[patIndex].getCondition().accepts(element, ctx)) {
      element = element.getParent();
    }
    if (element == null) return false;
    if (patIndex == myPatterns.length-1) return true; // the degenerate case of single pattern
    // make sure the rest matches, too
    do {
      element = element.getParent();
      patIndex += 1;
      if (element == null || element instanceof PsiDirectory) return false; // through the roof
      if (!myPatterns[patIndex].getCondition().accepts(element, ctx)) return false; // an unmatched gap
    } while (patIndex+1 < myPatterns.length);
    return true; // patterns finished without a fail
  }

  public boolean isClassAcceptable(Class hintClass) {
    return true;
  }
}
