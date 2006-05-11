/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.psi.impl.search;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;

/**
 * @author max
 */
public class ConstructorReferencesSearcher implements QueryExecutor<PsiReference, ReferencesSearch.SearchParameters> {
  public boolean execute(final ReferencesSearch.SearchParameters p, final Processor<PsiReference> consumer) {
    final PsiElement elt = p.getElementToSearch();
    if (elt instanceof PsiMethod && ((PsiMethod)elt).isConstructor()) {
      return new ConstructorReferencesSearchHelper(PsiManager.getInstance(elt.getProject()))
        .processConstructorReferences(consumer, (PsiMethod)p.getElementToSearch(), p.getScope(), p.isIgnoreAccessScope(), true);
    }
    return true;
  }
}
