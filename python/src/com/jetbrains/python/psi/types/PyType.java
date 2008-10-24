package com.jetbrains.python.psi.types;

import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyReferenceExpression;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a type of an expression.
 * @author yole
 */
public interface PyType {

  /**
   * Resolves an attribute of type.
   * @param name attribute name
   * @return attribute's definition element
   */
  @Nullable
  PsiElement resolveMember(final String name);

  /**
   * Proposes completion variants from type's attributes.
   * @param referenceExpression
   * @return
   */
  Object[] getCompletionVariants(final PyReferenceExpression referenceExpression);

  /**
   * @return name of the type
   */
  @Nullable
  String getName();

}
