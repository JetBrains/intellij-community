// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.search;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Processor;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.impl.PyCallExpressionNavigator;
import org.jetbrains.annotations.NotNull;


public final class PyInitReferenceSearchExecutor extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters> {
  @Override
  public void processQuery(@NotNull ReferencesSearch.SearchParameters queryParameters, @NotNull Processor<? super PsiReference> consumer) {
    PsiElement element = queryParameters.getElementToSearch();
    if (!(element instanceof PyFunction)) {
      return;
    }

    ApplicationManager.getApplication().runReadAction(() -> {
      String className;
      SearchScope searchScope;
      PyFunction function = (PyFunction)element;
      final PyClass pyClass = PyUtil.turnConstructorIntoClass(function);
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


      final var processor = new ClassInitializationProcessor(pyClass);
      queryParameters.getOptimizer().searchWord(className, searchScope, UsageSearchContext.IN_CODE, true, pyClass, processor);
    });
  }

  private static class ClassInitializationProcessor extends RequestResultProcessor {

    @NotNull
    private final SingleTargetRequestResultProcessor myProcessor;

    private ClassInitializationProcessor(@NotNull PyClass cls) {
      super(cls);
      myProcessor = new SingleTargetRequestResultProcessor(cls);
    }

    @Override
    public boolean processTextOccurrence(@NotNull PsiElement element,
                                         int offsetInElement,
                                         @NotNull Processor<? super PsiReference> consumer) {
      if (PyCallExpressionNavigator.getPyCallExpressionByCallee(element) != null) {
        return myProcessor.processTextOccurrence(element, offsetInElement, consumer);
      }

      return true;
    }
  }
}
