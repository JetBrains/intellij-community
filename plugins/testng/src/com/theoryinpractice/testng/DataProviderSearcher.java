// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.theoryinpractice.testng;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.DataProvider;

public class DataProviderSearcher extends QueryExecutorBase<PsiReference, MethodReferencesSearch.SearchParameters> {
  public DataProviderSearcher() {
    super(true);
  }

  @Override
  public void processQuery(@NotNull MethodReferencesSearch.SearchParameters queryParameters, @NotNull Processor<? super PsiReference> consumer) {
    final PsiMethod method = queryParameters.getMethod();

    final PsiAnnotation annotation = AnnotationUtil.findAnnotation(method, DataProvider.class.getName());
    if (annotation == null) return;
    final PsiAnnotationMemberValue dataProviderMethodName = annotation.findDeclaredAttributeValue("name");
    if (dataProviderMethodName != null) {
      final String providerName = StringUtil.unquoteString(dataProviderMethodName.getText());
      queryParameters.getOptimizer().searchWord(providerName, queryParameters.getEffectiveSearchScope(), UsageSearchContext.IN_STRINGS, true, method);
    }
  }

}
