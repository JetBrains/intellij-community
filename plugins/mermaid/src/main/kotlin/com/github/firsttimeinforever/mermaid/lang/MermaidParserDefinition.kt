package com.github.firsttimeinforever.mermaid.lang

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
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidLexer
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens
import com.github.firsttimeinforever.mermaid.lang.parser.MermaidElements
import com.github.firsttimeinforever.mermaid.lang.parser.MermaidParser
import com.github.firsttimeinforever.mermaid.lang.psi.MermaidFile

internal class MermaidParserDefinition: ParserDefinition {
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
    return TokenSet.create(MermaidTokens.COMMENT_TEXT)
  }

  override fun getStringLiteralElements(): TokenSet {
    return TokenSet.create(MermaidTokens.STRING_VALUE)
  }

  override fun getWhitespaceTokens(): TokenSet {
    return TokenSet.WHITE_SPACE
  }

  override fun createElement(node: ASTNode?): PsiElement {
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
