// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.pyi;

import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IFileElementType;
import com.jetbrains.python.PyPsiElementFactory;
import com.jetbrains.python.PythonParserDefinition;
import com.jetbrains.python.ast.impl.PyAstFileElementType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** The parser definition of a {@code .pyi} stub file. */
@ApiStatus.Internal
public final class PyiParserDefinition extends PythonParserDefinition {
  /** The file node type of a {@code .pyi} file. */
  public static final IFileElementType PYTHON_STUB_FILE = new PyAstFileElementType(PyiLanguageDialect.getInstance());

  @Override
  public @NotNull PsiFile createFile(@NotNull FileViewProvider viewProvider) {
    return PyPsiElementFactory.current().createPyiFile(viewProvider);
  }

  @Override
  public @NotNull IFileElementType getFileNodeType() {
    return PYTHON_STUB_FILE;
  }
}
