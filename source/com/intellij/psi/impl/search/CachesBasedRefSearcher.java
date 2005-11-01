/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.psi.impl.search;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.search.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import com.intellij.util.containers.HashSet;

import java.util.Set;

/**
 * @author max
 */
public class CachesBasedRefSearcher implements QueryExecutor<PsiReference, ReferencesSearch.SearchParameters> {
  public boolean execute(final ReferencesSearch.SearchParameters p, final Processor<PsiReference> consumer) {
    final PsiElement refElement = p.getElementToSearch();
    boolean ignoreAccessScope = p.isIgnoreAcccessScope();
    SearchScope originalScope = p.getScope();

    String text;
    if (refElement instanceof XmlAttributeValue) {
      text = ((XmlAttributeValue)refElement).getValue();
    }
    else if (refElement instanceof PsiNamedElement) {
      text = ((PsiNamedElement)refElement).getName();

      if (refElement instanceof PsiMetaOwner) {
        final PsiMetaData metaData = ((PsiMetaOwner)refElement).getMetaData();
        if (metaData!=null) text = metaData.getName();
      } else if (refElement instanceof JspFile) {
        final VirtualFile virtualFile = ((JspFile)refElement).getVirtualFile();
        text = virtualFile != null ? virtualFile.getNameWithoutExtension():text;
      }
    }
    else {
      return true;
    }

    if (text == null) return true;

    SearchScope searchScope;
    if (!ignoreAccessScope) {
      SearchScope accessScope = refElement.getUseScope();
      searchScope = originalScope.intersectWith(accessScope);
      if (searchScope == null) return true;
    }
    else {
      searchScope = originalScope;
    }

    final TextOccurenceProcessor processor1 = new TextOccurenceProcessor() {
      Set<PsiReference> myRefs = new HashSet<PsiReference>();
      PsiFile myLastFileProcessed = null;
      public boolean execute(PsiElement element, int offsetInElement) {
        final PsiFile currentfile = element.getContainingFile();
        if (myLastFileProcessed != currentfile) {
          myRefs = new HashSet<PsiReference>();
          myLastFileProcessed = currentfile;
        }

        final PsiReference reference = element.findReferenceAt(offsetInElement);
        if (reference == null) return true;
        if (!myRefs.add(reference)) return true;  //Hack:(
        if (reference.isReferenceTo(refElement)) {
          return consumer.process(reference);
        }
        else {
          return true;
        }
      }
    };


    short searchContext;

    if (refElement instanceof XmlAttributeValue) {
      searchContext = UsageSearchContext.IN_PLAIN_TEXT;
    }
    else {
      searchContext = UsageSearchContext.IN_CODE |
                      UsageSearchContext.IN_FOREIGN_LANGUAGES |
                      UsageSearchContext.IN_COMMENTS;
    }

    final PsiSearchHelper helper = PsiManager.getInstance(refElement.getProject()).getSearchHelper();

    if (!helper.processElementsWithWord(processor1,
                                        searchScope,
                                        text,
                                        searchContext,
                                        false
    )) {
      return false;
    }


    if (refElement instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)refElement;
      if (PropertyUtil.isSimplePropertyAccessor(method)) {
        final String propertyName = PropertyUtil.getPropertyName(method);
        //if (myManager.getNameHelper().isIdentifier(propertyName)) {
        if (searchScope instanceof GlobalSearchScope) {
          searchScope = GlobalSearchScope.getScopeRestrictedByFileTypes(
            (GlobalSearchScope)searchScope,
            StdFileTypes.JSP,
            StdFileTypes.JSPX,
            StdFileTypes.XML
          );
        }
        if (!helper.processElementsWithWord(processor1,
                                            searchScope,
                                            propertyName,
                                            UsageSearchContext.IN_FOREIGN_LANGUAGES,
                                            false)) {
          return false;
        }
        //}
      }
    }

    return true;
  }
}
