// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.search;

import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Processor;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.psi.PyElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class PyStringReferenceSearch extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters> {

  public PyStringReferenceSearch() {
    super(true);
  }

  @Override
  public void processQuery(final @NotNull ReferencesSearch.SearchParameters params,
                           final @NotNull Processor<? super PsiReference> consumer) {
    final PsiElement element = params.getElementToSearch();
    if (!(element instanceof PyElement) && !(element instanceof PsiDirectory)) {
      return;
    }

    SearchScope searchScope = params.getEffectiveSearchScope();
    if (searchScope instanceof GlobalSearchScope) {
      searchScope = GlobalSearchScope.getScopeRestrictedByFileTypes((GlobalSearchScope)searchScope, PythonFileType.INSTANCE);
    }

    String name = PySearchUtil.computeElementNameForStringSearch(element);

    if (StringUtil.isEmpty(name)) {
      return;
    }
    params.getOptimizer().searchWord(name, searchScope, UsageSearchContext.IN_STRINGS, true, element);
  }
}
