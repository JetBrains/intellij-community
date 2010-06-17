package com.jetbrains.python.psi.search;

import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.TextOccurenceProcessor;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import com.jetbrains.django.lang.template.DjangoTemplateFileType;
import com.jetbrains.django.lang.template.psi.impl.DjangoTemplateFileImpl;
import com.jetbrains.django.util.PythonUtil;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
public class PyStringReferenceSearch implements QueryExecutor<PsiReference, ReferencesSearch.SearchParameters> {
  public boolean execute(@NotNull final ReferencesSearch.SearchParameters params,
                         @NotNull final Processor<PsiReference> consumer) {
    final PsiElement element = params.getElementToSearch();
    if (!(element instanceof PyElement) && !(element instanceof PsiDirectory) && !(element instanceof DjangoTemplateFileImpl)) {
      return true;
    }

    SearchScope searchScope = params.getEffectiveSearchScope();
    if (searchScope instanceof GlobalSearchScope) {
      searchScope = GlobalSearchScope.getScopeRestrictedByFileTypes((GlobalSearchScope)searchScope, PythonFileType.INSTANCE, DjangoTemplateFileType.INSTANCE, StdFileTypes.XML, StdFileTypes.XHTML,
                                                                    HtmlFileType.INSTANCE
      );
    }

    return PythonUtil.searchElementStringReferences(consumer, element, searchScope);
  }
}
