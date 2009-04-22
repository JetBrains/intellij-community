package com.jetbrains.python.psi.resolve;

import com.intellij.psi.PsiElement;
import com.intellij.psi.scope.PsiScopeProcessor;

/**
 * @author yole
 */
public interface PyAsScopeProcessor extends PsiScopeProcessor {
  boolean execute(PsiElement element, String asName);
}
