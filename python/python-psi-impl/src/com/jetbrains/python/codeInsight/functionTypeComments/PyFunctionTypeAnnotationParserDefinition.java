/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.codeInsight.functionTypeComments;

import com.intellij.lang.PsiParser;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import com.jetbrains.python.PythonParserDefinition;
import com.jetbrains.python.codeInsight.functionTypeComments.psi.PyFunctionTypeAnnotationFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public final class PyFunctionTypeAnnotationParserDefinition extends PythonParserDefinition {

  @NotNull
  @Override
  public TokenSet getCommentTokens() {
    return TokenSet.EMPTY;
  }

  @Override
  public @NotNull PsiFile createFile(@NotNull FileViewProvider viewProvider) {
    return new PyFunctionTypeAnnotationFile(viewProvider);
  }

  @Override
  public @NotNull IFileElementType getFileNodeType() {
    return PyFunctionTypeAnnotationFileElementType.INSTANCE;
  }

  @NotNull
  @Override
  public PsiParser createParser(Project project) {
    return new PyFunctionTypeAnnotationParser();
  }
}
