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
import com.jetbrains.python.psi.PyClass;

/**
 * @author yole
 */
public class PyClassInheritorsSearch extends ExtensibleQueryFactory<PyClass, PyClassInheritorsSearch.SearchParameters> {
  public static final PyClassInheritorsSearch INSTANCE = new PyClassInheritorsSearch();

  public static class SearchParameters {
    private final PyClass mySuperClass;
    private final boolean myCheckDeepInheritance;

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
