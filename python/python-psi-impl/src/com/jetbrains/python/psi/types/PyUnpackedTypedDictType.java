// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Represents a keyword variadic type that has been unpacked from a {@code Unpack[TypedDict]} type.
 * <p>
 * This interface is used to model {@code Unpack[TypedDict]} in callable signatures,
 * where a TypedDict is unpacked into individual keyword parameters. Each parameter from the
 * TypedDict becomes an individual keyword-only callable parameter with its corresponding type.
 * <a href="https://peps.python.org/pep-0692/">PEP 692 – Using TypedDict for more precise **kwargs typing</a>
 * <p>
 * Example in Python:
 * <pre>{@code
 * class MovieInfo(TypedDict):
 *     title: str
 *     year: int
 *
 * def process_movie(**kwargs: Unpack[MovieInfo]): ...
 * # Equivalent to: def process_movie(*, title: str, year: int): ...
 * }</pre>
 * <p>
 * This interface provides access to both the unpacked parameters and the original TypedDict type
 */
@ApiStatus.Experimental
public interface PyUnpackedTypedDictType extends PyKeywordVariadicType {

  @NotNull List<PyCallableParameter> getUnpackedParameters(@NotNull TypeEvalContext context);

  @NotNull PyTypedDictType getTypedDictType();
}
