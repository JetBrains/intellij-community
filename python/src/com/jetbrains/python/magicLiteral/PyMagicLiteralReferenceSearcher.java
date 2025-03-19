// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.magicLiteral;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Processor;
import com.jetbrains.python.psi.StringLiteralExpression;
import org.jetbrains.annotations.NotNull;

/**
 * Searches for string usages on magic literals.
 * <strong>Install it</strong> as "referencesSearch" !
 * @author Ilya.Kazakevich
 */
final class PyMagicLiteralReferenceSearcher extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>  {

  @Override
  public void processQuery(final @NotNull ReferencesSearch.SearchParameters queryParameters, final @NotNull Processor<? super PsiReference> consumer) {
    ApplicationManager.getApplication().runReadAction(() -> {
      final PsiElement refElement = queryParameters.getElementToSearch();
      if (PyMagicLiteralTools.couldBeMagicLiteral(refElement)) {
        final String refText = ((StringLiteralExpression)refElement).getStringValue();
        if (!StringUtil.isEmpty(refText)) {
          final SearchScope searchScope = queryParameters.getEffectiveSearchScope();
          queryParameters.getOptimizer().searchWord(refText, searchScope, true, refElement);
        }
      }
    });
  }
}
