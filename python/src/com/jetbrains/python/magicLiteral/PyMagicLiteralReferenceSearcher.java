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
package com.jetbrains.python.magicLiteral;

import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Processor;
import com.jetbrains.python.psi.StringLiteralExpression;
import org.jetbrains.annotations.NotNull;

/**
 * Searches for string usages on magic literals.
 * <strong>Install it</strong> as "referencesSearch" !
 * @author Ilya.Kazakevich
 */
class PyMagicLiteralReferenceSearcher extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>  {

  @Override
  public void processQuery(@NotNull final ReferencesSearch.SearchParameters queryParameters, @NotNull final Processor<PsiReference> consumer) {
    new ReadAction() {
      @Override
      protected void run(@NotNull final Result result) throws Throwable {
        final PsiElement refElement = queryParameters.getElementToSearch();
        if (PyMagicLiteralTools.isMagicLiteral(refElement)) {
          final String refText = ((StringLiteralExpression)refElement).getStringValue();
          if (!StringUtil.isEmpty(refText)) {
            final SearchScope searchScope = queryParameters.getEffectiveSearchScope();
            queryParameters.getOptimizer().searchWord(refText, searchScope, true, refElement);
          }
        }
      }
    }.execute();
  }
}
