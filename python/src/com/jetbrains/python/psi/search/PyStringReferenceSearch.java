package com.jetbrains.python.psi.search;

import com.intellij.openapi.application.ApplicationManager;
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
    if (!(element instanceof PyElement) && !(element instanceof PsiDirectory)) {
      return true;
    }
    final String name = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      public String compute() {
        if (element instanceof PyFile) {
          return FileUtil.getNameWithoutExtension(((PyFile)element).getName());
        } else
          if (element instanceof PsiDirectory) {
            return ((PsiDirectory) element).getName();
          }
        else {
          return ((PyElement)element).getName();
        }
      }
    });
    if (StringUtil.isEmpty(name)) {
      return true;
    }
    SearchScope searchScope = params.getEffectiveSearchScope();
    if (searchScope instanceof GlobalSearchScope) {
      searchScope = GlobalSearchScope.getScopeRestrictedByFileTypes((GlobalSearchScope)searchScope, PythonFileType.INSTANCE);
    }

    final TextOccurenceProcessor processor = new TextOccurenceProcessor() {
      public boolean execute(PsiElement e, int offsetInElement) {
        final PsiReference[] refs = e.getReferences();
        for (PsiReference ref : refs) {
            if (ref.isReferenceTo(element)) {
              return consumer.process(ref);
            }
        }
        return true;
      }
    };

    return PsiManager.getInstance(element.getProject()).getSearchHelper().
      processElementsWithWord(processor, searchScope, name, UsageSearchContext.IN_STRINGS, true);
  }
}
