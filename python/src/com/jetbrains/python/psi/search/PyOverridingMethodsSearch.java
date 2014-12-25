/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.psi.search.searches.ExtensibleQueryFactory;
import com.intellij.util.Query;
import com.intellij.util.EmptyQuery;
import com.jetbrains.python.psi.PyFunction;

/**
 * @author yole
 */
public class PyOverridingMethodsSearch extends ExtensibleQueryFactory<PyFunction, PyOverridingMethodsSearch.SearchParameters> {
  public static final PyOverridingMethodsSearch INSTANCE = new PyOverridingMethodsSearch();

  public static class SearchParameters {
    private final PyFunction myFunction;
    private final boolean myCheckDeep;

    public SearchParameters(final PyFunction function, final boolean checkDeep) {
      myFunction = function;
      myCheckDeep = checkDeep;
    }

    public PyFunction getFunction() {
      return myFunction;
    }

    public boolean isCheckDeep() {
      return myCheckDeep;
    }
  }

  private PyOverridingMethodsSearch() {
    super("Pythonid");
  }

  public static Query<PyFunction> search(PyFunction function, boolean checkDeep) {
    if (function.getContainingClass() == null) return EmptyQuery.getEmptyQuery();
    return INSTANCE.createUniqueResultsQuery(new SearchParameters(function, checkDeep));
  }
}

