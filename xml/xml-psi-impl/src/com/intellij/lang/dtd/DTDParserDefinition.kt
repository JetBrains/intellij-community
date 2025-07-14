// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.dtd

import com.intellij.lang.ASTNode
import com.intellij.lang.LanguageUtil
import com.intellij.lang.ParserDefinition.SpaceRequirements
import com.intellij.lang.PsiParser
import com.intellij.lang.xml.XMLParserDefinition
import com.intellij.lexer.DtdLexer
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.parsing.xml.DtdParsing
import com.intellij.psi.impl.source.xml.XmlFileImpl
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.xml.XmlElementType
import com.intellij.psi.xml.XmlEntityContextType

class DTDParserDefinition :
  XMLParserDefinition() {

  override fun spaceExistenceTypeBetweenTokens(left: ASTNode, right: ASTNode): SpaceRequirements =
    LanguageUtil.canStickTokensTogetherByLexer(left, right, DtdLexer(false))

  override fun createFile(viewProvider: FileViewProvider): PsiFile = XmlFileImpl(viewProvider, XmlElementType.DTD_FILE)

  override fun createParser(project: Project?): PsiParser =
    PsiParser { root, builder ->
      DtdParsing(root, XmlEntityContextType.GENERIC_XML, builder).parse()
    }

  override fun getFileNodeType(): IFileElementType =
    XmlElementType.DTD_FILE

  override fun createLexer(project: Project?): Lexer =
    DtdLexer(false)
}
