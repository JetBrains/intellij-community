package com.jetbrains.python.psi.types;

import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyReferenceExpression;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface PyType {
  @Nullable
  PsiElement resolveMember(final String name);

  Object[] getCompletionVariants(final PyReferenceExpression referenceExpression);
}
