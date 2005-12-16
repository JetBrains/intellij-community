/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.psi.impl.search;

import com.intellij.psi.HierarchicalMethodSignature;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;

/**
 * @author peter
 */
public class MethodSuperSearcher implements QueryExecutor<MethodSignatureBackedByPsiMethod, SuperMethodsSearch.SearchParameters> {
  public boolean execute(final SuperMethodsSearch.SearchParameters queryParameters, final Processor<MethodSignatureBackedByPsiMethod> consumer) {
    final PsiClass parentClass = queryParameters.getPsiClass();
    final PsiMethod method = queryParameters.getMethod();

    HierarchicalMethodSignature signature = PsiSuperMethodImplUtil.getHierarchicalMethodSignature(parentClass, method);
    if (signature == null) return true;

    final boolean checkBases = queryParameters.isCheckBases();
    final boolean allowStaticMethod = queryParameters.isAllowStaticMethod();
    PsiMethod hisMethod = signature.getMethod();
    if (checkBases) {
      for (HierarchicalMethodSignature superSignature : signature.getSuperSignatures()) {
        if (suits(superSignature.getMethod(), method, allowStaticMethod)) {
          if (!consumer.process(superSignature)) return false;
        }
      }
    }
    else {
      if (parentClass.equals(hisMethod.getContainingClass())) {
        return consumer.process(signature);
      }
    }

    return true;
  }

  private boolean suits(final PsiMethod superMethod, final PsiMethod method, final boolean allowStaticMethod) {
    boolean hisStatic = superMethod.hasModifierProperty(PsiModifier.STATIC);
    if (hisStatic != method.hasModifierProperty(PsiModifier.STATIC) || !allowStaticMethod && hisStatic) return false;

    return method.getManager().getResolveHelper().isAccessible(superMethod, method, null);
  }

}
