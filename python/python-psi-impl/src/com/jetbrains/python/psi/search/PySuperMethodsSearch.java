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

import com.intellij.psi.PsiElement;
import com.intellij.psi.search.searches.ExtensibleQueryFactory;
import com.intellij.util.Query;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class PySuperMethodsSearch extends ExtensibleQueryFactory<PsiElement, PySuperMethodsSearch.SearchParameters> {
  public static final PySuperMethodsSearch INSTANCE = new PySuperMethodsSearch();

  private static PyFunction getBaseMethod(List<PsiElement> superMethods,
                                         PyClass containingClass) {

    for (PyClass ancestor : containingClass.getAncestorClasses(null)) {
      for (PsiElement method : superMethods) {
        if (ancestor.equals(((PyFunction)method).getContainingClass()))
          return (PyFunction)method;
      }
    }
    return (PyFunction) superMethods.get(superMethods.size()-1);
  }

  public static PyFunction findDeepestSuperMethod(PyFunction function) {
    TypeEvalContext context = TypeEvalContext.userInitiated(function.getProject(), null);
    List<PsiElement> superMethods = new ArrayList<>(search(function, true, context).findAll());
    while (superMethods.size() > 0) {
      function = getBaseMethod(superMethods, function.getContainingClass());
      superMethods = new ArrayList<>(search(function, true, context).findAll());
    }
    return function;
  }

  public static class SearchParameters {
    private final PyFunction myDerivedMethod;
    private final boolean myDeepSearch;
    private final TypeEvalContext myContext;

    public SearchParameters(final PyFunction derivedMethod, boolean deepSearch, @Nullable final TypeEvalContext context) {
      myDerivedMethod = derivedMethod;
      myDeepSearch = deepSearch;
      myContext = context;
    }

    @Nullable
    public TypeEvalContext getContext() {
      return myContext;
    }

    public PyFunction getDerivedMethod() {
      return myDerivedMethod;
    }

    public boolean isDeepSearch() {
      return myDeepSearch;
    }
  }

  private PySuperMethodsSearch() {
    super("Pythonid");
  }

  public static Query<PsiElement> search(final PyFunction derivedMethod, @Nullable final TypeEvalContext context) {
    final SearchParameters parameters = new SearchParameters(derivedMethod, false, context);
    return INSTANCE.createUniqueResultsQuery(parameters);
  }

  public static Query<PsiElement> search(final PyFunction derivedMethod, final boolean deepSearch, @Nullable final TypeEvalContext context) {
    final SearchParameters parameters = new SearchParameters(derivedMethod, deepSearch, context);
    return INSTANCE.createUniqueResultsQuery(parameters);
  }
}
