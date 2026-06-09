// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.lang

import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.mermaid.lang.lexer.MermaidLexer
import com.intellij.mermaid.lang.lexer.MermaidTokens
import com.intellij.mermaid.lang.parser.MermaidElements
import com.intellij.mermaid.lang.parser.MermaidParser
import com.intellij.mermaid.lang.psi.MermaidFile
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet

class MermaidParserDefinition: ParserDefinition {
  override fun createLexer(project: Project?): Lexer {
    return MermaidLexer()
  }

  override fun createParser(project: Project?): PsiParser {
    return MermaidParser()
  }

  override fun getFileNodeType(): IFileElementType {
    return FILE
  }

  override fun getCommentTokens(): TokenSet {
    return TokenSet.create(MermaidTokens.LINE_COMMENT)
  }

  override fun getStringLiteralElements(): TokenSet {
    return TokenSet.create(MermaidTokens.STRING_VALUE)
  }

  override fun getWhitespaceTokens(): TokenSet {
    return TokenSet.WHITE_SPACE
  }

  override fun createElement(node: ASTNode): PsiElement {
    return MermaidElements.Factory.createElement(node)
  }

  override fun createFile(viewProvider: FileViewProvider): PsiFile {
    return MermaidFile(viewProvider)
  }

  companion object {
    @JvmField
    val FILE = IFileElementType(MermaidLanguage)
  }
}
