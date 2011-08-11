package com.jetbrains.python.psi.search;

import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Processor;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyKeywordArgumentSearchExecutor extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters> {
  @Override
  public void processQuery(@NotNull ReferencesSearch.SearchParameters queryParameters, @NotNull final Processor<PsiReference> consumer) {
    final PsiElement element = queryParameters.getElementToSearch();
    if (!(element instanceof PyNamedParameter)) {
      return;
    }
    PyFunction owner = PsiTreeUtil.getParentOfType(element, PyFunction.class);
    if (owner == null) {
      return;
    }
    ReferencesSearch.search(owner, queryParameters.getScope()).forEach(new Processor<PsiReference>() {
      @Override
      public boolean process(PsiReference reference) {
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
      }
    });
  }
}
