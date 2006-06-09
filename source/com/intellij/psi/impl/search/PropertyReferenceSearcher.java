package com.intellij.psi.impl.search;

import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;

import java.util.List;

/**
 * @author ven
 */
public class PropertyReferenceSearcher implements QueryExecutor<PsiReference, ReferencesSearch.SearchParameters> {
  public boolean execute(final ReferencesSearch.SearchParameters queryParameters, final Processor<PsiReference> consumer) {
    final PsiElement refElement = queryParameters.getElementToSearch();
    if (refElement instanceof Property) {
      final String name = ((Property)refElement).getName();
      if (name == null) return true;
      final List<String> words = StringUtil.getWordsIn(name);
      if (words.isEmpty()) return true;
      final String lastWord = words.get(words.size() - 1);

      SearchScope searchScope = queryParameters.getEffectiveSearchScope();
      if (searchScope instanceof GlobalSearchScope) {
        searchScope = GlobalSearchScope.getScopeRestrictedByFileTypes((GlobalSearchScope)searchScope, StdFileTypes.JSP, StdFileTypes.JSPX);
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

      if (!helper.processElementsWithWord(processor, searchScope, lastWord, UsageSearchContext.IN_FOREIGN_LANGUAGES, false)) {
        return false;
      }
    }

    return true;
  }
}
