package com.intellij.ide.hierarchy.method;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.searches.SuperMethodsSearch;

final class MethodHierarchyUtil {
  public static PsiMethod findBaseMethodInClass(final PsiMethod derivedMethod, final PsiClass aClass, final boolean checkBases) {
    if (derivedMethod == null) return null; // base method is invalid

    return SuperMethodsSearch.search(derivedMethod, aClass, checkBases).findFirst();
  }
}
