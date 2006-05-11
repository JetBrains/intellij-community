/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.psi.impl.search;

import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Processor;
import com.intellij.util.Query;
import com.intellij.util.QueryExecutor;

/**
 * @author max
 */
public class PsiAnnotationMethodReferencesSearcher implements QueryExecutor<PsiReference, ReferencesSearch.SearchParameters> {
  public boolean execute(final ReferencesSearch.SearchParameters p, final Processor<PsiReference> consumer) {
    final PsiElement refElement = p.getElementToSearch();
    if (refElement instanceof PsiAnnotationMethod) {
      PsiMethod method = (PsiMethod)refElement;
      if (PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME.equals(method.getName()) && method.getParameterList().getParameters().length == 0) {
        final Query<PsiReference> query = ReferencesSearch.search(method.getContainingClass(), p.getScope(), p.isIgnoreAccessScope());
        return query.forEach(new Processor<PsiReference>() {
          public boolean process(final PsiReference reference) {
            if (reference instanceof PsiJavaCodeReferenceElement) {
              PsiJavaCodeReferenceElement javaReference = (PsiJavaCodeReferenceElement)reference;
              if (javaReference.getParent() instanceof PsiAnnotation) {
                PsiNameValuePair[] members = ((PsiAnnotation)javaReference.getParent()).getParameterList().getAttributes();
                if (members.length == 1 && members[0].getNameIdentifier() == null) {
                  if (!consumer.process(members[0].getReference())) return false;
                }
              }
            }
            return true;
          }
        });
      }
    }

    return true;
  }
}
