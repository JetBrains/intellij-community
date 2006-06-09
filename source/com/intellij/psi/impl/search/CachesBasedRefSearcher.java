/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.psi.impl.search;

import com.intellij.lang.StdLanguages;
import com.intellij.psi.*;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.TextOccurenceProcessor;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.xml.XmlEntityDecl;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author max
 */
public class CachesBasedRefSearcher implements QueryExecutor<PsiReference, ReferencesSearch.SearchParameters> {
  public boolean execute(final ReferencesSearch.SearchParameters p, final Processor<PsiReference> consumer) {
    final PsiElement refElement = p.getElementToSearch();

    String text = null;
    if (refElement instanceof XmlAttributeValue) {
      text = ((XmlAttributeValue)refElement).getValue();
    }
    else if (refElement instanceof PsiFile) {
      final VirtualFile vFile = ((PsiFile)refElement).getVirtualFile();
      if (vFile != null) {
        text = vFile.getNameWithoutExtension();
      }
    }
    else if (refElement instanceof PsiNamedElement) {
      text = ((PsiNamedElement)refElement).getName();

      if (refElement instanceof PsiMetaOwner) {
        final PsiMetaData metaData = ((PsiMetaOwner)refElement).getMetaData();
        if (metaData != null) text = metaData.getName();
      }
    }

    if (text == null) return true;

    SearchScope searchScope = p.getEffectiveSearchScope();
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

    short searchContext;
    if (refElement instanceof XmlEntityDecl) {
      searchContext = UsageSearchContext.IN_PLAIN_TEXT;
    }
    else {
      searchContext = UsageSearchContext.IN_CODE |
                      UsageSearchContext.IN_FOREIGN_LANGUAGES |
                      UsageSearchContext.IN_COMMENTS;
    }

    final PsiSearchHelper helper = PsiManager.getInstance(refElement.getProject()).getSearchHelper();

    if (!helper.processElementsWithWord(processor,
                                        searchScope,
                                        text,
                                        searchContext,
                                        refElement.getLanguage() == StdLanguages.JAVA  //todo: temporary hack!!
    )) {
      return false;
    }

    return true;
  }
}
