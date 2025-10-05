// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.xml

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.idea.AppModeAssertions
import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.ParserDefinition.SpaceRequirements
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.lexer.XmlLexer
import com.intellij.lexer.createXmlLexer
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.parsing.xml.XmlParser
import com.intellij.psi.impl.source.xml.XmlFileImpl
import com.intellij.psi.impl.source.xml.stub.XmlStubBasedElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.UnsupportedNodeElementTypeException
import com.intellij.psi.xml.XmlElementType
import com.intellij.psi.xml.XmlTokenType

open class XMLParserDefinition :
  ParserDefinition {

  override fun createLexer(project: Project?): Lexer =
    createXmlLexer()

  override fun getFileNodeType(): IFileElementType =
    XmlElementType.XML_FILE

  override fun getWhitespaceTokens(): TokenSet =
    XmlTokenType.WHITESPACES

  override fun getCommentTokens(): TokenSet =
    XmlTokenType.COMMENTS

  override fun getStringLiteralElements(): TokenSet =
    TokenSet.EMPTY

  override fun createParser(project: Project?): PsiParser =
    XmlParser()

  override fun createElement(node: ASTNode): PsiElement {
    val elementType = node.elementType

    return when {
      elementType is XmlStubBasedElementType<*> -> elementType.createPsi(node)
      AppModeAssertions.isFrontend() -> ASTWrapperPsiElement(node)

      else -> throw UnsupportedNodeElementTypeException(node)
    }
  }

  override fun createFile(viewProvider: FileViewProvider): PsiFile = XmlFileImpl(viewProvider, XmlElementType.XML_FILE)

  override fun spaceExistenceTypeBetweenTokens(left: ASTNode, right: ASTNode): SpaceRequirements =
    canStickTokensTogether(left, right)
}

fun canStickTokensTogether(left: ASTNode, right: ASTNode): SpaceRequirements =
  when {
    left.elementType === XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN
    || right.elementType === XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN
      -> SpaceRequirements.MUST_NOT

    left.elementType === XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER
    && right.elementType === XmlTokenType.XML_NAME
      -> SpaceRequirements.MUST

    left.elementType === XmlTokenType.XML_NAME
    && right.elementType === XmlTokenType.XML_NAME
      -> SpaceRequirements.MUST

    left.elementType === XmlTokenType.XML_TAG_NAME
    && right.elementType === XmlTokenType.XML_NAME
      -> SpaceRequirements.MUST

    else -> SpaceRequirements.MAY
  }
