// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiElement;
import com.intellij.util.Processor;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyWithAncestors;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

public interface PyClassLikeType extends PyCallableType, PyWithAncestors, PyInstantiableType<PyClassLikeType> {

  @Nullable
  @NlsSafe
  String getClassQName();

  @NotNull
  List<PyClassLikeType> getSuperClassTypes(@NotNull TypeEvalContext context);

  @Nullable
  List<? extends RatedResolveResult> resolveMember(final @NotNull String name,
                                                   @Nullable PyExpression location,
                                                   @NotNull AccessDirection direction,
                                                   @NotNull PyResolveContext resolveContext,
                                                   boolean inherited);

  // TODO: Pull to PyType at next iteration
  /**
   * Visits all class members. This method is better then bare class since it uses type info and supports not only classes but
   * class-like structures as well. Consider using user-friendly wrapper {@link PyTypeUtil#getMembersOfType(PyClassLikeType, Class, boolean, TypeEvalContext)}
   *
   * @param processor visitor
   * @param inherited call on parents too
   * @param context   context to be used to resolve types
   * @see PyTypeUtil#getMembersOfType(PyClassLikeType, Class, boolean, TypeEvalContext)
   */
  void visitMembers(@NotNull Processor<? super PsiElement> processor, boolean inherited, @NotNull TypeEvalContext context);

  @NotNull
  Set<String> getMemberNames(boolean inherited, @NotNull TypeEvalContext context);

  boolean isValid();

  @Nullable
  PyClassLikeType getMetaClassType(@NotNull TypeEvalContext context, boolean inherited);

  @Override
  default <T> T acceptTypeVisitor(@NotNull PyTypeVisitor<T> visitor) {
    return visitor.visitPyClassLikeType(this);
  }
}
