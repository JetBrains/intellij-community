/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.psi.impl.search;

import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.MethodSignature;
import com.intellij.util.QueryExecutor;
import com.intellij.util.Processor;
import com.intellij.util.containers.HashMap;

import java.util.Map;

/**
 * @author peter
 */
public class MethodSuperSearcher implements QueryExecutor<PsiMethod, SuperMethodsSearch.SearchParameters> {
  public boolean execute(final SuperMethodsSearch.SearchParameters queryParameters, final Processor<PsiMethod> consumer) {
    PsiClass aClass = queryParameters.getPsiClass();
    final PsiMethod method = queryParameters.getMethod();
    MethodSignature signature = method.getSignature(queryParameters.getSubstitutor());
    final boolean checkBases = queryParameters.isCheckBases();

    final Map<PsiClass, PsiSubstitutor> substitutors = new HashMap<PsiClass, PsiSubstitutor>();
    for (final PsiMethod baseMethod : aClass.findMethodsByName(method.getName(), checkBases)) {
      if (baseMethod.getParameterList().getParameters().length == method.getParameterList().getParameters().length) {
        PsiSubstitutor substitutor = substitutors.get(baseMethod.getContainingClass());
        if (substitutor == null) {
          substitutor = TypeConversionUtil.getClassSubstitutor(baseMethod.getContainingClass(), aClass, PsiSubstitutor.EMPTY);
          if (substitutor == null) continue;
          substitutors.put(baseMethod.getContainingClass(), substitutor);
        }

        if (MethodSignatureUtil.isSubsignature(signature, baseMethod.getSignature(substitutor))) {
          if (!consumer.process(baseMethod)) return false;
        }
      }
    }
    return true;
  }
}
