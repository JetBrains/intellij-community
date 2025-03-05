// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Represents a type parameter that should be substituted with a single type during the unification process.
 * Normally, it's declared using {@code TypeVar} function from the "typing" module, as in
 * <pre>{@code
 * from typing import TypeVar
 *
 * T = TypeVar('T')
 * }</pre>
 * but can also come from other sources, such as docstrings.
 */
public interface PyTypeVarType extends PyTypeParameterType, PyInstantiableType<PyTypeVarType> {
  @NotNull List<@Nullable PyType> getConstraints();

  /**
   * Returns the upper bound for this type parameter if it was specified.
   * <p>
   * For instance, for the following declaration
   * <pre>{@code
   * from typing import TypeVar
   *
   * T = TypeVar('T', bound=list[int])
   * }</pre>
   * this method should return the type corresponding to the type hint {@code list[int]}.
   * <p>
   * See the section <a href="https://peps.python.org/pep-0484/#type-variables-with-an-upper-bound">Type variables with an upper bound</a>
   * in PEP 484.
   */
  @Nullable PyType getBound();
}
