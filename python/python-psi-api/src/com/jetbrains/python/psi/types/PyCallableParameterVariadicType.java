// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types;

import org.jetbrains.annotations.ApiStatus;

/**
 * Represents an entity in the type system that stands for a parameter list of a callable type.
 */
@ApiStatus.Experimental
public non-sealed interface PyCallableParameterVariadicType extends PyVariadicType {
}
