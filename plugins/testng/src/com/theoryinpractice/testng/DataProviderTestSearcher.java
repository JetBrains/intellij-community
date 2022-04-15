// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import org.testng.annotations.Test;

public class DataProviderTestSearcher extends QueryExecutorBase<PsiReference, MethodReferencesSearch.SearchParameters> {
  public DataProviderTestSearcher() {
    super(true);
  }

  @Override
  public void processQuery(@NotNull MethodReferencesSearch.SearchParameters queryParameters, @NotNull Processor<? super PsiReference> consumer) {
    final PsiMethod method = queryParameters.getMethod();

    final PsiAnnotation annotation = AnnotationUtil.findAnnotation(method, Test.class.getName());
    if (annotation == null) return;
    final PsiAnnotationMemberValue dataProviderMethodName = annotation.findDeclaredAttributeValue("dataProvider");
    if (dataProviderMethodName != null) {
      final String providerName = StringUtil.unquoteString(dataProviderMethodName.getText());
      queryParameters.getOptimizer().searchWord(providerName, queryParameters.getEffectiveSearchScope(), UsageSearchContext.IN_STRINGS, true, method);
    }
  }
}
