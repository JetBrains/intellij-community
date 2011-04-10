package com.intellij.structuralsearch.equivalence;

import com.intellij.psi.PsiElement;

/**
 * @author Eugene.Kudelevsky
 */
public interface EquivalenceDescriptorHelper {
  int getSubtreeHash(PsiElement element);
}
