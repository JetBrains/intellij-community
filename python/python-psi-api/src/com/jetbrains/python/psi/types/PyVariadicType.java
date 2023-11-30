// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types;

import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A marker interface for type forms that can be "unpacked" into a series of types.
 * Normally, such constructs cannot be used on their own in type hints, and can appear only inside other generic types.
 * Two variants of such types described in <a href="https://peps.python.org/pep-0646/">PEP 646 – Variadic Generics</a> are 
 * TypeVarTuples and unpacked tuple types.
 *
 * @see PyTypeVarTupleType
 * @see PyUnpackedTupleType
 */
@ApiStatus.Experimental
public interface PyVariadicType extends PyType {
  @Override
  default boolean isBuiltin() {
    return false;
  }

  @Override
  default void assertValid(String message) {

  }

  @Override
  @Nullable
  default List<? extends RatedResolveResult> resolveMember(@NotNull String name,
                                                           @Nullable PyExpression location,
                                                           @NotNull AccessDirection direction,
                                                           @NotNull PyResolveContext resolveContext) {
    return null;
  }

  @Override
  default Object[] getCompletionVariants(String completionPrefix, PsiElement location, ProcessingContext context) {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }
}
