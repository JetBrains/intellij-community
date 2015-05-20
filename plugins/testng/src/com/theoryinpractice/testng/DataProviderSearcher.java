/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.theoryinpractice.testng;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
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
  public void processQuery(@NotNull MethodReferencesSearch.SearchParameters queryParameters, @NotNull Processor<PsiReference> consumer) {
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
