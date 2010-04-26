package com.jetbrains.python.psi.types;

import com.intellij.psi.PsiElement;

/**
 * @author yole
 */
public interface PyTypeReference extends PyType {
  PyType resolve(PsiElement context);
}
