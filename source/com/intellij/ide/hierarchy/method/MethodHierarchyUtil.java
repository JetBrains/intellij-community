package com.intellij.ide.hierarchy.method;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.psi.search.searches.SuperMethodsSearch;

final class MethodHierarchyUtil {
  public static PsiMethod findBaseMethodInClass(final PsiMethod baseMethod, final PsiClass aClass, final boolean checkBases) {
    if (baseMethod == null) return null; // base method is invalid

    PsiSubstitutor substitutor = TypeConversionUtil.getClassSubstitutor(baseMethod.getContainingClass(), aClass, PsiSubstitutor.EMPTY);
    if (substitutor == null) return null;
    return SuperMethodsSearch.search(baseMethod, substitutor, aClass, checkBases).findFirst();
  }
}
