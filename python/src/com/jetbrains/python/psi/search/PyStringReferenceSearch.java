// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.jetbrains.python.psi.PyUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
public class PyStringReferenceSearch extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters> {

  public PyStringReferenceSearch() {
    super(true);
  }

  public void processQuery(@NotNull final ReferencesSearch.SearchParameters params,
                           @NotNull final Processor<? super PsiReference> consumer) {
    final PsiElement element = params.getElementToSearch();
    if (!(element instanceof PyElement) && !(element instanceof PsiDirectory)) {
      return;
    }

    SearchScope searchScope = params.getEffectiveSearchScope();
    if (searchScope instanceof GlobalSearchScope) {
      searchScope = GlobalSearchScope.getScopeRestrictedByFileTypes((GlobalSearchScope)searchScope, PythonFileType.INSTANCE);
    }

    String name = PyUtil.computeElementNameForStringSearch(element);

    if (StringUtil.isEmpty(name)) {
      return;
    }
    params.getOptimizer().searchWord(name, searchScope, UsageSearchContext.IN_STRINGS, true, element);
  }
}
