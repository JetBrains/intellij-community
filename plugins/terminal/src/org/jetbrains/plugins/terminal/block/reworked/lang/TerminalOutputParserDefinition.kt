// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked.lang

import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.EmptyLexer
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiUtilCore

internal class TerminalOutputParserDefinition : ParserDefinition {
  override fun createLexer(project: Project?): Lexer {
    return EmptyLexer()
  }

  override fun createParser(project: Project?): PsiParser {
    throw UnsupportedOperationException("Not supported")
  }

  override fun getFileNodeType(): IFileElementType {
    return TerminalOutputTokenTypes.FILE
  }

  override fun createFile(viewProvider: FileViewProvider): PsiFile {
    return TerminalOutputPsiFile(viewProvider)
  }

  override fun getWhitespaceTokens(): TokenSet {
    return TokenSet.EMPTY
  }

  override fun getCommentTokens(): TokenSet {
    return TokenSet.EMPTY
  }

  override fun getStringLiteralElements(): TokenSet {
    return TokenSet.EMPTY
  }

  override fun createElement(node: ASTNode?): PsiElement {
    return PsiUtilCore.NULL_PSI_ELEMENT
  }
}