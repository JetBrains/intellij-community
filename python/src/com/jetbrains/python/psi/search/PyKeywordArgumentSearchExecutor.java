// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.search;

import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Processor;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyKeywordArgumentSearchExecutor extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters> {
  @Override
  public void processQuery(@NotNull final ReferencesSearch.SearchParameters queryParameters, @NotNull final Processor<? super PsiReference> consumer) {
    final PsiElement element = queryParameters.getElementToSearch();
    if (!(element instanceof PyNamedParameter)) {
      return;
    }
    final ScopeOwner owner = ReadAction.compute(() -> ScopeUtil.getScopeOwner(element));
    if (!(owner instanceof PyFunction)) {
      return;
    }
    ReferencesSearch.search(owner, queryParameters.getScopeDeterminedByUser()).forEach(reference -> {
      final PsiElement refElement = reference.getElement();
      final PyCallExpression call = PsiTreeUtil.getParentOfType(refElement, PyCallExpression.class);
      if (call != null && PsiTreeUtil.isAncestor(call.getCallee(), refElement, false)) {
        final PyArgumentList argumentList = call.getArgumentList();
        if (argumentList != null) {
          final PyKeywordArgument keywordArgument = argumentList.getKeywordArgument(((PyNamedParameter)element).getName());
          if (keywordArgument != null) {
            return consumer.process(keywordArgument.getReference());
          }
        }
      }
      return true;
    });
  }
}
