/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.psi.impl.search;

import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiClass;
import com.intellij.util.QueryExecutor;
import com.intellij.util.Processor;

/**
 * @author peter
 */
public class MethodSuperSearcher implements QueryExecutor<PsiMethod, SuperMethodsSearch.SearchParameters> {
  public boolean execute(final SuperMethodsSearch.SearchParameters queryParameters, final Processor<PsiMethod> consumer) {
    final PsiClass psiClass = queryParameters.getPsiClass();
    final PsiMethod[] methods = psiClass.findMethodsBySignature(queryParameters.getMethod(), queryParameters.isCheckBases());
    for (PsiMethod psiMethod : methods) {
      if (!consumer.process(psiMethod)) return false;
    }
    return true;
  }
}
