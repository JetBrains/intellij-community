// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types;


import org.jetbrains.annotations.ApiStatus;

/**
 * A marker interface for variadic types that represent keyword parameters in Python callables.
 * <p>
 * This interface is used to represent types that can be unpacked as keyword arguments, such as
 * {@code **kwargs} parameters or {@code **} unpacking in TypedDict contexts. Unlike positional
 * variadic types, keyword variadic types associate parameter names with their corresponding values.
 * <p>
 * Examples of usage in Python type hints:
 * <pre>{@code
 * class MyDict(TypedDict):
 *     name: str
 * def bar(**kwargs: Unpack[MyDict]): ...  # Unpacked TypedDict parameters
 * }</pre>
 *
 * @see PyVariadicType
 * @see PyUnpackedTypedDictType
 */
@ApiStatus.Experimental
public non-sealed interface PyKeywordVariadicType extends PyVariadicType {

}
