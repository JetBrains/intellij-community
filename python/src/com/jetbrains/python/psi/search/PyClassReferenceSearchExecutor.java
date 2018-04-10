/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
  public void processQuery(@NotNull ReferencesSearch.SearchParameters queryParameters, @NotNull final Processor<PsiReference> consumer) {
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
