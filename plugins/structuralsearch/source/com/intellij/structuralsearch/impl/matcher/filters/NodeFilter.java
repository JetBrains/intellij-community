package com.intellij.structuralsearch.impl.matcher.filters;

import com.intellij.psi.PsiElement;

/**
 * Base class for tree filtering
 */
public interface NodeFilter {
  boolean accepts(PsiElement element);
}
