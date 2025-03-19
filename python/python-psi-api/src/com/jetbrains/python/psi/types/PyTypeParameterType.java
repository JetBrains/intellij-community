// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types;

import com.intellij.openapi.util.Ref;
import com.jetbrains.python.psi.PyQualifiedNameOwner;
import com.jetbrains.python.psi.PyTargetExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents an entity in the type system that can be used to parameterize other types making them "generic".
 * A typical example of a type parameter is PEP 484 {@code TypeVar} like "T" in the following:
 * <pre>{@code
 *
 * from typing import TypeVar
 *
 * T = TypeVar('T')
 *
 * def identity(x: T) -> T:
 *     return x
 *
 * }</pre>
 * <p>
 * making the type of "identity" parameterized by "T".
 * Other examples of type parameters are {@code ParamSpec} and {@code TypeVarTuple}.
 * <p>
 * Declarations using "magical" factories from typing, such as {@code T = TypeVar("T")}, are the most common source of type parameters.
 * The corresponding {@link PyTargetExpression} can be retrieved with {@link #getDeclarationElement()}.
 * However, they can also come from docstrings or be created dynamically by type providers and, hence, not have a physical declaration.
 */
public interface PyTypeParameterType extends PyType {
  /**
   * Returns the name of this type parameter, such as "T" for the {@link PyTypeVarType} instance introduced by {@code T = TypeVar("T")}.
   */
  @Override
  @NotNull String getName();

  /**
   * Normally, a type parameter must be bound to a specific declaration to avoid collisions with other parameters with the same name.
   * <p>
   * For instance, here
   * <pre>{@code
   * from typing import TypeVar
   *
   * T = TypeVar('T')
   *
   * def min(xs: list[T]) -> T | None:
   *     ...
   * }</pre>
   * <p>
   * "T" is bound to the "min" function definition, and here
   * <pre>{@code
   * from typing import Generic, TypeVar
   *
   * T = TypeVar('T')
   *
   * class ListOps(Generic[T]):
   *     def min(self, xs: list[T]) -> T | None:
   *         ...
   * }</pre>
   * <p>
   * it is bound to the enclosing class "ListOps".
   * <p>
   * In the following, type parameters "T" of "f" and "g" are unrelated to each other, despite sharing the same name:
   * <pre>{@code
   * from typing import TypeVar
   *
   * T = TypeVar('T')
   *
   * def f(x: T) -> T:
   *     return g(x)
   *
   * def g(x: T) -> T:
   *     return f(x)
   * }</pre>
   * <p>
   * See the section <a href="https://peps.python.org/pep-0484/#scoping-rules-for-type-variables">Scoping rules for type variables</a>
   * in PEP 484.
   * <p>
   * Results of {@code getScopeOwner()} and {@link #getName()} constitute unique "coordinates" of a given type parameter.
   */
  @Nullable PyQualifiedNameOwner getScopeOwner();

  default @Nullable Ref<? extends PyType> getDefaultType() {
    return null;
  }
}
