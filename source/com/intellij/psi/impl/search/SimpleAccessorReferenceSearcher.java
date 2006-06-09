package com.intellij.psi.impl.search;

import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.search.*;
import com.intellij.util.QueryExecutor;
import com.intellij.util.Processor;
import com.intellij.openapi.fileTypes.StdFileTypes;

/**
 * @author ven
 */
public class SimpleAccessorReferenceSearcher implements QueryExecutor<PsiReference, ReferencesSearch.SearchParameters> {
  public boolean execute(final ReferencesSearch.SearchParameters queryParameters, final Processor<PsiReference> consumer) {
    final PsiElement refElement = queryParameters.getElementToSearch();
    if (refElement instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)refElement;
      if (PropertyUtil.isSimplePropertyAccessor(method)) {
        final String propertyName = PropertyUtil.getPropertyName(method);
        SearchScope searchScope = queryParameters.getEffectiveSearchScope();
        if (searchScope instanceof GlobalSearchScope) {
          searchScope = GlobalSearchScope.getScopeRestrictedByFileTypes(
            (GlobalSearchScope)searchScope,
            StdFileTypes.JSP,
            StdFileTypes.JSPX,
            StdFileTypes.XML
          );
        }

        final PsiSearchHelper helper = PsiManager.getInstance(refElement.getProject()).getSearchHelper();
        final TextOccurenceProcessor processor = new TextOccurenceProcessor() {
          public boolean execute(PsiElement element, int offsetInElement) {
            final PsiReference[] refs = element.getReferences();
            for (PsiReference ref : refs) {
              if (ref.getRangeInElement().contains(offsetInElement)) {
                if (ref.isReferenceTo(refElement)) {
                  return consumer.process(ref);
                }
              }
            }
            return true;
          }
        };

        if (!helper.processElementsWithWord(processor,
                                            searchScope,
                                            propertyName,
                                            UsageSearchContext.IN_FOREIGN_LANGUAGES,
                                            false)) {
          return false;
        }
      }
    }
    return true;
  }
}
