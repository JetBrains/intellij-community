// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.search;

import com.google.common.base.Strings;
import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.openapi.application.ReadAction;
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
import com.jetbrains.python.psi.PyUtil;
import org.jetbrains.annotations.NotNull;

public class PyClassReferenceSearchExecutor extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters> {
  public void processQuery(@NotNull ReferencesSearch.SearchParameters queryParameters, @NotNull final Processor<? super PsiReference> consumer) {
    PyClass pyClass = PyUtil.as(queryParameters.getElementToSearch(), PyClass.class);
    if (pyClass == null) {
      return;
    }

    ReadAction.run(() -> {
      final String className = pyClass.getName();
      if (Strings.isNullOrEmpty(className)) {
        return;
      }
      final PyFunction initMethod = pyClass.findMethodByName(PyNames.INIT, false, null);
      if (initMethod == null) {
        return;
      }
      SearchScope searchScope = queryParameters.getEffectiveSearchScope();
      if (searchScope instanceof GlobalSearchScope) {
        searchScope = GlobalSearchScope.getScopeRestrictedByFileTypes((GlobalSearchScope)searchScope, PythonFileType.INSTANCE);
      }
      queryParameters.getOptimizer().searchWord(className, searchScope, UsageSearchContext.IN_CODE, true, initMethod);
    });
  }
}
