package com.jetbrains.python.codeInsight;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;

/**
 * Completion contributor that stores the original PSI element being completed, for use by filters
 * that know how to handle it.
 * The element is stored under {@link #ORG_ELT} key, and its offset in the original tree under {@link #ORG_OFFSET}, as user data
 * in the usual mock element passed to filters.
 * User: dcheryasov
 * Date: Dec 3, 2009 10:30:29 AM
 */
public abstract class PySeeingOriginalCompletionContributor extends CompletionContributor {
  public static Key<PsiElement> ORG_ELT = Key.create("PyKeywordCompletionContributor original element");
  public static Key<Integer> ORG_OFFSET = Key.create("PyKeywordCompletionContributor original offset");

  @Override
  public void fillCompletionVariants(CompletionParameters parameters, CompletionResultSet result) {
    final PsiElement original = parameters.getPosition();
    try {
      original.putUserData(ORG_ELT, parameters.getOriginalPosition());
      original.putUserData(ORG_OFFSET, parameters.getOffset());
      // we'll be safe accessing original file in patterns, because pattern checks run in a ReadAction.
      super.fillCompletionVariants(parameters, result);
    }
    finally {
      // help gc a bit
      original.putUserData(ORG_ELT, null);
      original.putUserData(ORG_OFFSET, null);
    }
  }
}
