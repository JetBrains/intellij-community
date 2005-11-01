/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.uiDesigner.binding;

import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;

/**
 * @author max
 */
public class FormReferencesSearcher implements QueryExecutor<PsiReference, ReferencesSearch.SearchParameters> {
  public boolean execute(final ReferencesSearch.SearchParameters p, final Processor<PsiReference> consumer) {
    final SearchScope scope = p.getScope();
    if (scope instanceof GlobalSearchScope) {
      final PsiElement refElement = p.getElementToSearch();
      if (refElement instanceof PsiPackage) {
        if (!UIFormUtil.processReferencesInUIForms(consumer, (PsiPackage)refElement, (GlobalSearchScope)scope)) return false;
      }
      else if (refElement instanceof PsiClass) {
        if (!UIFormUtil.processReferencesInUIForms(consumer, (PsiClass)refElement, (GlobalSearchScope)scope)) return false;
      }
      else if (refElement instanceof PsiField) {
        if (!UIFormUtil.processReferencesInUIForms(consumer, (PsiField)refElement, (GlobalSearchScope)scope)) return false;
      }
      else if (refElement instanceof Property) {
        if (!UIFormUtil.processReferencesInUIForms(consumer, (Property)refElement, (GlobalSearchScope)scope)) return false;
      }
      else if (refElement instanceof PropertiesFile) {
        if (!UIFormUtil.processReferencesInUIForms(consumer, (PropertiesFile)refElement, (GlobalSearchScope)scope)) return false;
      }
    }

    return true;
  }
}
