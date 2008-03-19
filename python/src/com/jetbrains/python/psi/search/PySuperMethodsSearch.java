package com.jetbrains.python.psi.search;

import com.intellij.psi.search.searches.ExtensibleQueryFactory;
import com.intellij.util.Query;
import com.jetbrains.python.psi.PyFunction;

/**
 * @author yole
 */
public class PySuperMethodsSearch extends ExtensibleQueryFactory<PyFunction, PySuperMethodsSearch.SearchParameters> {
  public static PySuperMethodsSearch INSTANCE = new PySuperMethodsSearch();

  public static class SearchParameters {
    private PyFunction myDerivedMethod;

    public SearchParameters(final PyFunction derivedMethod) {
      myDerivedMethod = derivedMethod;
    }

    public PyFunction getDerivedMethod() {
      return myDerivedMethod;
    }
  }

  private PySuperMethodsSearch() {
    super("Pythonid");
  }

  public static Query<PyFunction> search(final PyFunction derivedMethod) {
    final SearchParameters parameters = new SearchParameters(derivedMethod);
    return INSTANCE.createUniqueResultsQuery(parameters);
  }
}
