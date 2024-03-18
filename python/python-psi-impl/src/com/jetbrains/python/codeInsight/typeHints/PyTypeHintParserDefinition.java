// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.typeHints;

import com.intellij.lang.PsiParser;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import com.jetbrains.python.PythonParserDefinition;
import org.jetbrains.annotations.NotNull;

public final class PyTypeHintParserDefinition extends PythonParserDefinition {

  @NotNull
  @Override
  public TokenSet getCommentTokens() {
    return TokenSet.EMPTY;
  }

  @Override
  @NotNull
  public PsiFile createFile(@NotNull FileViewProvider viewProvider) {
    return new PyTypeHintFile(viewProvider);
  }

  @Override
  @NotNull
  public IFileElementType getFileNodeType() {
    return PyTypeHintFileElementType.INSTANCE;
  }

  @NotNull
  @Override
  public PsiParser createParser(Project project) {
    return new PyTypeHintParser();
  }
}
