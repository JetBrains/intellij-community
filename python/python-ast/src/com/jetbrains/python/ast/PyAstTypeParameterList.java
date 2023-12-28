// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.ast;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Represents a list of generic Type Parameters.<br>
 * e.g. {@code def foo[T, U](x: T | U): ...}<br>
 * where {@code [T, U]} is the list of generic Type Parameters.<br>
 * For more information see <a href="https://peps.python.org/pep-0695/">PEP 695</a>
 */
@ApiStatus.Experimental
public interface PyAstTypeParameterList extends PyAstElement {

  @NotNull
  List<? extends PyAstTypeParameter> getTypeParameters();
}
