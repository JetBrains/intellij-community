// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.theoryinpractice.testng;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import static com.theoryinpractice.testng.util.TestNGUtil.DATA_PROVIDER_ANNOTATION_FQN;
import static com.theoryinpractice.testng.util.TestNGUtil.getAttributeValue;

public class DataProviderSearcher extends QueryExecutorBase<PsiReference, MethodReferencesSearch.SearchParameters> {
  public DataProviderSearcher() {
    super(true);
  }

  @Override
  public void processQuery(@NotNull MethodReferencesSearch.SearchParameters queryParameters, @NotNull Processor<? super PsiReference> consumer) {
    final PsiMethod method = queryParameters.getMethod();

    final PsiAnnotation annotation = AnnotationUtil.findAnnotation(method, DATA_PROVIDER_ANNOTATION_FQN);
    if (annotation == null) return;
    String name = getAttributeValue(annotation, "name");
    final String providerName = name != null ? name : method.getName();
    queryParameters.getOptimizer().searchWord(providerName, queryParameters.getEffectiveSearchScope(), UsageSearchContext.IN_STRINGS, true, method);
  }
}
