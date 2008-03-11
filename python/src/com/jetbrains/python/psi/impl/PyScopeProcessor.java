package com.jetbrains.python.psi.impl;

import com.intellij.psi.PsiElement;
import com.intellij.psi.scope.PsiScopeProcessor;

/**
 * @author yole
 */
public interface PyScopeProcessor extends PsiScopeProcessor {
  boolean execute(PsiElement element, String asName);
}
