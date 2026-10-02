// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.pyi;

import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IFileElementType;
import com.jetbrains.python.PythonParserDefinition;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class PyiParserDefinition extends PythonParserDefinition {
  public static final IFileElementType PYTHON_STUB_FILE = new PyiFileElementType(PyiLanguageDialect.getInstance());

  @Override
  public @NotNull PsiFile createFile(@NotNull FileViewProvider viewProvider) {
    return new PyiFile(viewProvider);
  }

  @Override
  public @NotNull IFileElementType getFileNodeType() {
    return PYTHON_STUB_FILE;
  }
}
