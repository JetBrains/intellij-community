// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.typeHints;

import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PythonFileType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

class PyTypeHintFileType extends PythonFileType {

  public static final @NotNull PyTypeHintFileType INSTANCE = new PyTypeHintFileType();

  private PyTypeHintFileType() {
    super(PyTypeHintDialect.INSTANCE);
  }

  @Override
  public @NotNull @NonNls String getName() {
    return "PythonTypeHint";
  }

  @Override
  public @NotNull String getDescription() {
    return PyPsiBundle.message("filetype.python.type.hint.description");
  }

  @Override
  public @NotNull String getDefaultExtension() {
    return "typeHint";
  }
}
