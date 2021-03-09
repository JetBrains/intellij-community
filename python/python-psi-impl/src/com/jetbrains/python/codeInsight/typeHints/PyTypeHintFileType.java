// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.typeHints;

import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PythonFileType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

class PyTypeHintFileType extends PythonFileType {

  @NotNull
  public static final PyTypeHintFileType INSTANCE = new PyTypeHintFileType();

  private PyTypeHintFileType() {
    super(PyTypeHintDialect.INSTANCE);
  }

  @NotNull
  @Override
  @NonNls
  public String getName() {
    return "PythonTypeHint";
  }

  @NotNull
  @Override
  public String getDescription() {
    return PyPsiBundle.message("filetype.python.type.hint.description");
  }

  @NotNull
  @Override
  public String getDefaultExtension() {
    return "typeHint";
  }
}
