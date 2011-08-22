package com.jetbrains.python.psi.search;

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Processor;
import com.jetbrains.django.lang.template.psi.impl.DjangoTemplateFileImpl;
import com.jetbrains.django.util.PythonUtil;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.psi.PyElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
public class PyStringReferenceSearch extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters> {
  public void processQuery(@NotNull final ReferencesSearch.SearchParameters params,
                           @NotNull final Processor<PsiReference> consumer) {
    final PsiElement element = params.getElementToSearch();
    if (!(element instanceof PyElement) && !(element instanceof PsiDirectory) && !(element instanceof DjangoTemplateFileImpl)) {
      return;
    }

    AccessToken token = ApplicationManager.getApplication().acquireReadActionLock();
    String name;
    SearchScope searchScope;
    try {
      searchScope = params.getEffectiveSearchScope();
      if (searchScope instanceof GlobalSearchScope) {
        searchScope = GlobalSearchScope.getScopeRestrictedByFileTypes((GlobalSearchScope)searchScope, PythonFileType.INSTANCE);
      }

      name = PythonUtil.computeElementNameForStringSearch(element);
    }
    finally {
      token.finish();
    }

    if (StringUtil.isEmpty(name)) {
      return;
    }
    params.getOptimizer().searchWord(name, searchScope, UsageSearchContext.IN_STRINGS, true, element);
  }
}
