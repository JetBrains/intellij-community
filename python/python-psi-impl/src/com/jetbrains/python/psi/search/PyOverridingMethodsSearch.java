// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.search;

import com.intellij.psi.search.searches.ExtensibleQueryFactory;
import com.intellij.util.EmptyQuery;
import com.intellij.util.Query;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.ApiStatus;


@ApiStatus.Internal
public final class PyOverridingMethodsSearch extends ExtensibleQueryFactory<PyFunction, PyOverridingMethodsSearch.SearchParameters> {
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

