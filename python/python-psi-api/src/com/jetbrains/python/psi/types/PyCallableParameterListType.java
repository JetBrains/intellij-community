// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Represents a series of {@link PyCallableParameter} used either as a part of {@link PyCallableType} or a substitution
 * for {@link PyParamSpecType}.
 */
@ApiStatus.Experimental
public interface PyCallableParameterListType extends PyCallableParameterVariadicType {
  @NotNull List<PyCallableParameter> getParameters();
}
