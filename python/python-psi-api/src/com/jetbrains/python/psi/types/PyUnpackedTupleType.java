// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Represents an ordered series of types, or an unbound repetition of a single type.
 * <p>
 * It can be thought of as a content of a standard tuple type, but not associated with the corresponding built-in class.
 * An unpacked tuple can appear only inside other types, such as types of generic classes and callables.
 * Unpacked tuple types serve as concrete substitutions for TypeVarTuples in the result of type inference.
 * <p>
 * An unpacked tuple type can be declared with {@code *tuple[T1, T2, ...]} syntax.
 *
 * @see <a href="https://peps.python.org/pep-0646/#unpacking-tuple-types">PEP 646 – Variadic Generics</a>
 * @see PyTypeVarTupleType
 */
public non-sealed interface PyUnpackedTupleType extends PyPositionalVariadicType {
  /**
   * Returns types contained inside this unpacked tuple type.
   * <p>
   * For an unbound unpacked tuple type, the result is always a single type, e.g. for {@code *tuple[int, ...]} the returned
   * list will consist only of a {@link PyClassType} for the built-in class int.
   * <p>
   * The contained types are not flattened, e.g. for {@code *tuple[*tuple[int, str], str]} the list will
   * contain two types {@link PyUnpackedTupleType} for {@code *tuple[int, str]} and {@link PyClassType} for {@code str}.
   */
  @NotNull List<PyType> getElementTypes();

  /**
   * Returns true if this unpacked tuple type represents an unlimited repetition of a single type as opposed to
   * a finite series of types.
   * <p>
   * Such unpacked tuples types are declared with the syntax {@code *tuple[T, ...]}.
   * <p>
   * Note that inner variadic types are not considered, i.e. in {@code *tuple[int, *tuple[str, ...]]} or {@code *tuple[*Ts]}
   * the top-level unpacked tuple type is still considered bound or "concrete", even though it represents a
   * tuple of types of unknown length.
   */
  boolean isUnbound();
}
