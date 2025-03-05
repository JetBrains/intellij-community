// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types;

/**
 * Represents a variadic type parameter that can be substituted with an arbitrary number of types in the corresponding generic type.
 * A series of concrete types it can be replaced with is normally represented with {@link PyUnpackedTupleType}.
 * <p>
 * It's declared either using the {@code TypeVarTuple} function from the "typing" module, as in
 * <pre>{@code
 * from typing import TypeVar
 *
 * Ts = TypeVarTuple('Ts')
 *
 * class MyGeneric(Generic[*Ts]):
 *     ...
 * }</pre>
 * or inline, without any imports, with the new syntax for generic classes and type aliases introduced in PEP 695, as in
 * <pre>{@code
 * class MyGeneric[*Ts]:
 *     ...
 * }</pre>
 *
 * @see <a href="https://peps.python.org/pep-0646/#type-variable-tuples">PEP 646 – Variadic Generics</a>
 * @see PyUnpackedTupleType
 */
public non-sealed interface PyTypeVarTupleType extends PyTypeParameterType, PyPositionalVariadicType {
}
