// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
}
