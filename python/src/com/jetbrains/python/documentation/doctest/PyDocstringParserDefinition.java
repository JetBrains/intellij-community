/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
public class PyDocstringParserDefinition extends PythonParserDefinition {
  public static final IFileElementType PYTHON_DOCSTRING_FILE = new PyDocstringFileElementType(PyDocstringLanguageDialect
                                                                                                .getInstance());

  @NotNull
  public Lexer createLexer(Project project) {
    return new PyDocstringLexer();
  }

  @NotNull
  @Override
  public PsiParser createParser(Project project) {
    return new PyDocstringParser();
  }


  @NotNull
  @Override
  public TokenSet getWhitespaceTokens() {
    return TokenSet.orSet(super.getWhitespaceTokens(), TokenSet.create(PyDocstringTokenTypes.DOTS));
  }

  @Override
  public IFileElementType getFileNodeType() {
    return PYTHON_DOCSTRING_FILE;
  }

  @Override
  public PsiFile createFile(FileViewProvider viewProvider) {
    return new PyDocstringFile(viewProvider);
  }
}
