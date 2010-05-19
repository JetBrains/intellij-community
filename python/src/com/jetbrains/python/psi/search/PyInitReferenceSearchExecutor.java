package com.jetbrains.python.psi.search;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ReferenceRange;
import com.intellij.psi.search.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;

/**
 * @author yole
 */
public class PyInitReferenceSearchExecutor implements QueryExecutor<PsiReference, ReferencesSearch.SearchParameters> {
  public boolean execute(ReferencesSearch.SearchParameters queryParameters, final Processor<PsiReference> consumer) {
    PsiElement element = queryParameters.getElementToSearch();
    if (!(element instanceof PyFunction)) {
      return true;
    }
    final PyFunction function = (PyFunction)element;
    if (!PyNames.INIT.equals(function.getName())) {
      return true;
    }
    final PyClass pyClass = function.getContainingClass();
    if (pyClass == null) {
      return true;
    }
    final String className = pyClass.getName();
    if (className == null) {
      return true;
    }

    SearchScope searchScope = queryParameters.getEffectiveSearchScope();
    if (searchScope instanceof GlobalSearchScope) {
      searchScope = GlobalSearchScope.getScopeRestrictedByFileTypes((GlobalSearchScope)searchScope, PythonFileType.INSTANCE);
    }

    final PsiSearchHelper helper = function.getManager().getSearchHelper();
    final TextOccurenceProcessor processor = new TextOccurenceProcessor() {
      public boolean execute(PsiElement element, int offsetInElement) {
        final PsiReference[] refs = element.getReferences();
        for (PsiReference ref : refs) {
          if (ReferenceRange.containsOffsetInElement(ref, offsetInElement)) {
            if (ref.isReferenceTo(function)) {
              return consumer.process(ref);
            }
          }
        }
        return true;
      }
    };

    return helper.processElementsWithWord(processor,
                                          searchScope,
                                          className,
                                          UsageSearchContext.IN_CODE,
                                          false);
  }
}
