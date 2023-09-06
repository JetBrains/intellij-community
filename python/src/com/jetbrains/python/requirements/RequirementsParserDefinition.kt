// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements

import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import com.jetbrains.python.requirements.lexer.RequirementsLexerAdapter
import com.jetbrains.python.requirements.psi.RequirementsTypes
import com.jetbrains.python.requirements.psi.parser.RequirementsParser


class RequirementsParserDefinition : ParserDefinition {
  override fun createLexer(project: Project): Lexer {
    return RequirementsLexerAdapter()
  }



  override fun getStringLiteralElements(): TokenSet {
    return TokenSet.EMPTY
  }

  override fun createParser(project: Project): PsiParser {
    return RequirementsParser()
  }

  override fun getFileNodeType(): IFileElementType {
    return FILE
  }

  override fun getCommentTokens(): TokenSet {
    return RequirementsTokenSets.COMMENTS
  }

  override fun createFile(viewProvider: FileViewProvider): PsiFile {
    return RequirementsFile(viewProvider)
  }

  override fun createElement(node: ASTNode): PsiElement {
    return RequirementsTypes.Factory.createElement(node)
  }

  companion object {
    val FILE = IFileElementType(RequirementsLanguage.INSTANCE)
  }
}