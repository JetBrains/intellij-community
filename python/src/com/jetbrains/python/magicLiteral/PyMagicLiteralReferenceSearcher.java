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
