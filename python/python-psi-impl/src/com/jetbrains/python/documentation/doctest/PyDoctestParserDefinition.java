// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.documentation.doctest;

import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import com.jetbrains.python.PythonParserDefinition;
import org.jetbrains.annotations.NotNull;

/**
 * User : ktisha
 */
public final class PyDoctestParserDefinition extends PythonParserDefinition {
  public static final IFileElementType PYTHON_DOCTEST_FILE = new PyDocstringFileElementType(PyDoctestLanguageDialect
                                                                                                .getInstance());

  @Override
  public @NotNull Lexer createLexer(Project project) {
    return new PyDoctestLexer();
  }

  @Override
  public @NotNull PsiParser createParser(Project project) {
    return new PyDoctestParser();
  }


  @Override
  public @NotNull TokenSet getWhitespaceTokens() {
    return TokenSet.orSet(super.getWhitespaceTokens(), TokenSet.create(PyDoctestTokenTypes.DOTS));
  }

  @Override
  public @NotNull IFileElementType getFileNodeType() {
    return PYTHON_DOCTEST_FILE;
  }

  @Override
  public @NotNull PsiFile createFile(@NotNull FileViewProvider viewProvider) {
    return new PyDoctestFile(viewProvider);
  }
}
