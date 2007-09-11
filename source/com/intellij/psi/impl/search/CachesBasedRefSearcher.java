/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.psi.impl.search;

import com.intellij.lang.StdLanguages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.*;
import com.intellij.psi.meta.PsiMetaBaseOwner;
import com.intellij.psi.meta.PsiMetaDataBase;
import com.intellij.psi.search.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlEntityDecl;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;

/**
 * @author max
 */
public class CachesBasedRefSearcher implements QueryExecutor<PsiReference, ReferencesSearch.SearchParameters> {
  public static final boolean DEBUG = false;

  public boolean execute(final ReferencesSearch.SearchParameters p, final Processor<PsiReference> consumer) {
    final PsiElement refElement = p.getElementToSearch();

    String text = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      public String compute() {
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
          if (refElement instanceof PsiMetaBaseOwner) {
            final PsiMetaDataBase metaData = ((PsiMetaBaseOwner)refElement).getMetaData();
            if (metaData != null) text = metaData.getName();
          }
        }

        if (text == null && refElement instanceof PsiMetaBaseOwner) {
          final PsiMetaDataBase metaData = ((PsiMetaBaseOwner)refElement).getMetaData();
          if (metaData != null) text = metaData.getName();
        }
        return text;
      }
    });
    if (StringUtil.isEmpty(text)) return true;
    if (DEBUG) System.out.println("Searching for :'"+text+"'");
      
    SearchScope searchScope = ApplicationManager.getApplication().runReadAction(new Computable<SearchScope>() {
      public SearchScope compute() {
        return p.getEffectiveSearchScope();
      }
    });
    if (DEBUG) System.out.println("searchScope = " + searchScope);
    final boolean ignoreInjectedPsi = searchScope instanceof LocalSearchScope && ((LocalSearchScope)searchScope).isIgnoreInjectedPsi();

    final TextOccurenceProcessor processor = new TextOccurenceProcessor() {
      public boolean execute(PsiElement element, int offsetInElement) {
        if (DEBUG) System.out.println("!!! About to check "+element);
        if (ignoreInjectedPsi && element instanceof PsiLanguageInjectionHost) return true;
        final PsiReference[] refs = element.getReferences();
        for (PsiReference ref : refs) {
          if (DEBUG) System.out.println("!!!!!!!!!!!!!! Ref "+ref);
          if (ref.getRangeInElement().contains(offsetInElement)) {
            if (DEBUG) System.out.println("!!!!!!!!!!!!!!!!!!!!! Ref "+ref + " contains");
            if (ref.isReferenceTo(refElement)) {
              if (DEBUG) System.out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!   Found ref "+ref);
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
    return helper.processElementsWithWord(processor, searchScope, text, searchContext, 
                                          refElement.getLanguage() == StdLanguages.JAVA //todo: temporary hack!!
    );

  }
}
