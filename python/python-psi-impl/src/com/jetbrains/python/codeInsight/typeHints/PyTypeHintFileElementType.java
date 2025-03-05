// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.typeHints;

import com.jetbrains.python.psi.PyFileElementType;
import org.jetbrains.annotations.NotNull;

class PyTypeHintFileElementType extends PyFileElementType {

  public static final @NotNull PyTypeHintFileElementType INSTANCE = new PyTypeHintFileElementType();

  private PyTypeHintFileElementType() {
    super(PyTypeHintDialect.INSTANCE);
  }

  @Override
  public @NotNull String getExternalId() {
    return "PyTypeHint.ID";
  }
}
