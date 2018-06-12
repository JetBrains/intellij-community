// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.search;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Processor;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyInitReferenceSearchExecutor extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters> {
  public void processQuery(@NotNull ReferencesSearch.SearchParameters queryParameters, @NotNull final Processor<? super PsiReference> consumer) {
    PsiElement element = queryParameters.getElementToSearch();
    if (!(element instanceof PyFunction)) {
      return;
    }

    ApplicationManager.getApplication().runReadAction(() -> {
      String className;
      SearchScope searchScope;
      PyFunction function;
      function = (PyFunction)element;
      if (!PyNames.INIT.equals(function.getName())) {
        return;
      }
      final PyClass pyClass = function.getContainingClass();
      if (pyClass == null) {
        return;
      }
      className = pyClass.getName();
      if (className == null) {
        return;
      }

      searchScope = queryParameters.getEffectiveSearchScope();
      if (searchScope instanceof GlobalSearchScope) {
        searchScope = GlobalSearchScope.getScopeRestrictedByFileTypes((GlobalSearchScope)searchScope, PythonFileType.INSTANCE);
      }


      queryParameters.getOptimizer().searchWord(className, searchScope, UsageSearchContext.IN_CODE, true, function);
    });
  }
}
