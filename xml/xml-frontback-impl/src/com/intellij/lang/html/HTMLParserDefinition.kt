// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.html

import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.ParserDefinition.SpaceRequirements
import com.intellij.lang.PsiParser
import com.intellij.lang.xml.canStickTokensTogether
import com.intellij.lexer.HtmlLexer
import com.intellij.lexer.Lexer
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.xml.XmlElementType
import com.intellij.psi.xml.XmlTokenType

open class HTMLParserDefinition :
  ParserDefinition {

  private val elementFactory: BasicHtmlElementFactory by lazy {
    service<BasicHtmlElementFactory>()
  }

  override fun createLexer(project: Project?): Lexer =
    HtmlLexer()

  override fun getFileNodeType(): IFileElementType =
    XmlElementType.HTML_FILE

  override fun getWhitespaceTokens(): TokenSet =
    XmlTokenType.WHITESPACES

  override fun getCommentTokens(): TokenSet =
    XmlTokenType.COMMENTS

  override fun getStringLiteralElements(): TokenSet =
    TokenSet.EMPTY

  override fun createParser(project: Project?): PsiParser =
    HTMLParser()

  override fun createElement(node: ASTNode): PsiElement =
    elementFactory.createElement(node)

  override fun createFile(viewProvider: FileViewProvider): PsiFile =
    elementFactory.createFile(viewProvider)

  override fun spaceExistenceTypeBetweenTokens(left: ASTNode, right: ASTNode): SpaceRequirements =
    canStickTokensTogether(left, right)
}
