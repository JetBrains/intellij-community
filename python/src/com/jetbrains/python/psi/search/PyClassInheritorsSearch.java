package com.jetbrains.python.psi.search;

import com.intellij.psi.search.searches.ExtensibleQueryFactory;
import com.intellij.util.Query;
import com.jetbrains.python.psi.PyClass;

/**
 * @author yole
 */
public class PyClassInheritorsSearch extends ExtensibleQueryFactory<PyClass, PyClassInheritorsSearch.SearchParameters> {
  public static PyClassInheritorsSearch INSTANCE = new PyClassInheritorsSearch();

  public static class SearchParameters {
    private PyClass mySuperClass;

    public SearchParameters(final PyClass superClass) {
      mySuperClass = superClass;
    }

    public PyClass getSuperClass() {
      return mySuperClass;
    }
  }

  private PyClassInheritorsSearch() {
    super("Pythonid");
  }

  public static Query<PyClass> search(final PyClass superClass) {
    final SearchParameters parameters = new SearchParameters(superClass);
    return INSTANCE.createUniqueResultsQuery(parameters);
  }
}
