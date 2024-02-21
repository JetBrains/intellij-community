// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types;

import org.jetbrains.annotations.ApiStatus;


/**
 * Two variants of such types described in <a href="https://peps.python.org/pep-0646/">PEP 646 – Variadic Generics</a> are
 * TypeVarTuples and unpacked tuple types.
 *
 * @see PyTypeVarTupleType
 * @see PyUnpackedTupleType
 */
@ApiStatus.Experimental
public interface PyPositionalVariadicType extends PyVariadicType {

}
