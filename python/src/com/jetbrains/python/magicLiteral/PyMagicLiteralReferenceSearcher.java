// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
class PyMagicLiteralReferenceSearcher extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>  {

  @Override
  public void processQuery(@NotNull final ReferencesSearch.SearchParameters queryParameters, @NotNull final Processor<? super PsiReference> consumer) {
    ApplicationManager.getApplication().runReadAction(() -> {
      final PsiElement refElement = queryParameters.getElementToSearch();
      if (PyMagicLiteralTools.isMagicLiteral(refElement)) {
        final String refText = ((StringLiteralExpression)refElement).getStringValue();
        if (!StringUtil.isEmpty(refText)) {
          final SearchScope searchScope = queryParameters.getEffectiveSearchScope();
          queryParameters.getOptimizer().searchWord(refText, searchScope, true, refElement);
        }
      }
    });
  }
}
