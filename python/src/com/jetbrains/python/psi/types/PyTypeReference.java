package com.jetbrains.python.psi.types;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface PyTypeReference extends PyType {
  @Nullable
  PyType resolve(PsiElement context, TypeEvalContext typeEvalContext);
}
