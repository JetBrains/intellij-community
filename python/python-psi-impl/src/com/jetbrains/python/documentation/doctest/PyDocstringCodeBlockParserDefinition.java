// Copyright 2000-2026 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.documentation.doctest;

import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IFileElementType;
import com.jetbrains.python.PythonParserDefinition;
import org.jetbrains.annotations.NotNull;

/**
 * This parses a plain Python code block from a docstring into a {@link PyDocstringCodeBlockFile}.
 * The block has no doctest prompt, so this class reuses the plain Python lexer and parser from
 * {@link PythonParserDefinition}.
 */
public final class PyDocstringCodeBlockParserDefinition extends PythonParserDefinition {
  public static final IFileElementType PYTHON_DOCSTRING_CODE_BLOCK_FILE =
    new PyDocstringCodeBlockFileElementType(PyDocstringCodeBlockLanguageDialect.getInstance());

  @Override
  public @NotNull IFileElementType getFileNodeType() {
    return PYTHON_DOCSTRING_CODE_BLOCK_FILE;
  }

  @Override
  public @NotNull PsiFile createFile(@NotNull FileViewProvider viewProvider) {
    return new PyDocstringCodeBlockFile(viewProvider);
  }
}
