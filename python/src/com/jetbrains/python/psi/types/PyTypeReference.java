package com.jetbrains.python.psi.types;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface PyTypeReference extends PyType {
  @Nullable
  PyType resolve(@Nullable PsiElement context, @NotNull TypeEvalContext typeEvalContext);
}
