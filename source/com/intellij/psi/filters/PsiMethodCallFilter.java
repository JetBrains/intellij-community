package com.intellij.psi.filters;

import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import org.jetbrains.annotations.NonNls;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class PsiMethodCallFilter implements ElementFilter {
  @NonNls private String myClassName;
  @NonNls private Set<String> myMethodNames;


  public PsiMethodCallFilter(@NonNls final String className, @NonNls final String... methodNames) {
    myClassName = className;
    myMethodNames = new HashSet<String>(Arrays.asList(methodNames));
  }

  public boolean isAcceptable(Object element, PsiElement context) {
    if (element instanceof PsiMethodCallExpression) {
      final PsiMethodCallExpression callExpression = (PsiMethodCallExpression)element;
      final PsiMethod psiMethod = callExpression.resolveMethod();
      if (psiMethod != null) {
        if (!myMethodNames.contains(psiMethod.getName())) {
          return false;
        }
        final PsiClass psiClass = psiMethod.getContainingClass();
        final PsiClass expectedClass = JavaPsiFacade.getInstance(psiClass.getProject()).findClass(myClassName, psiClass.getResolveScope());
        return InheritanceUtil.isInheritorOrSelf(psiClass, expectedClass, true);
      }
    }
    return false;
  }

  public boolean isClassAcceptable(Class hintClass) {
    return PsiMethodCallExpression.class.isAssignableFrom(hintClass);
  }

  @NonNls
  public String toString() {
    return "methodcall(" + myClassName + "." + myMethodNames + ")";
  }
}
