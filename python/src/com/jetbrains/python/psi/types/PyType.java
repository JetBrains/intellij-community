package com.jetbrains.python.psi.types;

import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyReferenceExpression;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * @author yole
 */
public interface PyType {

  @Nullable
  PsiElement resolveMember(final String name);

  Object[] getCompletionVariants(final PyReferenceExpression referenceExpression);

}
