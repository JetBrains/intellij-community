package com.intellij.structuralsearch.impl.matcher.filters;

import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafElement;

/**
 * Default searching filter
 */
public class DefaultFilter {
  public static boolean accepts(PsiElement element, PsiElement element2) {
    if (element instanceof LeafElement && element2 instanceof LeafElement) {
      return ((LeafElement)element).getElementType() == ((LeafElement)element2).getElementType();
    }
    return element.getClass()==element2.getClass();
  }
}
