// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface PyUnpackedTupleType extends PyVariadicType {
  @NotNull List<PyType> getElementTypes();

  boolean isUnbound();
}
