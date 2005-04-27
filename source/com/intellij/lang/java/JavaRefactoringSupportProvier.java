package com.intellij.lang.java;

import com.intellij.lang.refactoring.RefactoringSupportProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiField;

/**
 * @author ven
 */
public class JavaRefactoringSupportProvier implements RefactoringSupportProvider {
  public boolean isSafeDeleteAvailable(PsiElement element) {
    return element instanceof PsiClass
           || element instanceof PsiMethod
           || element instanceof PsiField;
  }
}
