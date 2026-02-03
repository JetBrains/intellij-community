/*
 * Copyright 2000-2025 JetBrains s.r.o.
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
package com.jetbrains.python.codeInsight.typeRepresentation

import com.intellij.lang.PsiParser
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import com.jetbrains.python.PythonParserDefinition
import com.jetbrains.python.codeInsight.typeRepresentation.psi.PyTypeRepresentationFile

class PyTypeRepresentationParserDefinition : PythonParserDefinition() {
  override fun getCommentTokens(): TokenSet = TokenSet.EMPTY

  override fun createFile(viewProvider: FileViewProvider): PsiFile = PyTypeRepresentationFile(viewProvider)

  override fun getFileNodeType(): IFileElementType = PyTypeRepresentationFileElementType

  override fun createParser(project: Project?): PsiParser = PyTypeRepresentationParser()
}
