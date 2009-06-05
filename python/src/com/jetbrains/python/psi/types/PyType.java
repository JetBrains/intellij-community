package com.jetbrains.python.psi.types;

import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.psi.PyReferenceExpression;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

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
   * @param referenceExpression which is to be completed
   * @param context to share state between nested invocations
   * @return completion variants good for {@link com.intellij.psi.PsiReference#getVariants} return value.
   */
  Object[] getCompletionVariants(final PyReferenceExpression referenceExpression, ProcessingContext context);

  /**
   * Context key for access to a set of names already found by variant search. 
   */
  Key<Set<String>> CTX_NAMES = new Key<Set<String>>("Completion variants names");

  /**
   * @return name of the type
   */
  @Nullable
  String getName();

}
