package com.intellij.structuralsearch.impl.matcher.filters;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceExpression;

/**
 * Default searching filter
 */
public class DefaultFilter {
  public static boolean accepts(PsiElement element, PsiElement element2) {
    return element.getClass()==element2.getClass();
  }
}
