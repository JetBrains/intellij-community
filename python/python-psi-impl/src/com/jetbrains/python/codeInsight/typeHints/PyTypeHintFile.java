// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.typeHints;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyExpressionCodeFragment;
import com.jetbrains.python.psi.impl.PyFileImpl;
import org.jetbrains.annotations.NotNull;

public class PyTypeHintFile extends PyFileImpl implements PyExpressionCodeFragment {

  PyTypeHintFile(@NotNull FileViewProvider viewProvider) {
    super(viewProvider, PyTypeHintDialect.INSTANCE);
  }

  @Override
  public @NotNull FileType getFileType() {
    return PyTypeHintFileType.INSTANCE;
  }

  @Override
  public String toString() {
    return "TypeHint:" + getName();
  }

  @Override
  public LanguageLevel getLanguageLevel() {
    // The same as for .pyi files
    return LanguageLevel.getLatest();
  }
}

