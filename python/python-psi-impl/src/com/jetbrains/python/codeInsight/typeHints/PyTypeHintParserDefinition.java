// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.typeHints;

import com.intellij.lang.PsiParser;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IFileElementType;
import com.jetbrains.python.PythonParserDefinition;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class PyTypeHintParserDefinition extends PythonParserDefinition {
  @Override
  public @NotNull PsiFile createFile(@NotNull FileViewProvider viewProvider) {
    return new PyTypeHintFile(viewProvider);
  }

  @Override
  public @NotNull IFileElementType getFileNodeType() {
    return PyTypeHintFileElementType.INSTANCE;
  }

  @Override
  public @NotNull PsiParser createParser(Project project) {
    return new PyTypeHintParser();
  }
}
