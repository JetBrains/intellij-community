package com.intellij.structuralsearch.impl.matcher.strategies;

import com.intellij.psi.PsiElement;


/**
 * CommonStrategy of metching process
 */
public interface MatchingStrategy {
  boolean continueMatching(PsiElement start);
}
