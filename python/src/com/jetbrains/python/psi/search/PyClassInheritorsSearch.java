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
    private boolean myCheckDeepInheritance;

    public SearchParameters(final PyClass superClass, final boolean checkDeepInheritance) {
      mySuperClass = superClass;
      myCheckDeepInheritance = checkDeepInheritance;
    }

    public PyClass getSuperClass() {
      return mySuperClass;
    }

    public boolean isCheckDeepInheritance() {
      return myCheckDeepInheritance;
    }
  }

  private PyClassInheritorsSearch() {
    super("Pythonid");
  }

  public static Query<PyClass> search(final PyClass superClass, final boolean checkDeepInheritance) {
    final SearchParameters parameters = new SearchParameters(superClass, checkDeepInheritance);
    return INSTANCE.createUniqueResultsQuery(parameters);
  }
}
