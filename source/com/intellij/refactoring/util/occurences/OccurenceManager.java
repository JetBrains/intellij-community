package com.intellij.refactoring.util.occurences;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;

/**
 * @author dsl
 */
public interface OccurenceManager {
  PsiExpression[] getOccurences();
  boolean isInFinalContext();
  PsiElement getAnchorStatementForAll();

  PsiElement getAnchorStatementForAllInScope(PsiElement scope);
}
