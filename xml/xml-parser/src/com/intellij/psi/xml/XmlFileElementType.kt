// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.xml

import com.intellij.lang.ASTNode
import com.intellij.lang.xml.XMLLanguage
import com.intellij.platform.syntax.emptySyntaxElementTypeSet
import com.intellij.platform.syntax.psi.PsiSyntaxBuilderFactory
import com.intellij.platform.syntax.psi.registerParse
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.parsing.xml.REPARSE_XML_TAG_BY_NAME
import com.intellij.psi.tree.IFileElementType
import com.intellij.xml.syntax.XmlParser

class XmlFileElementType : IFileElementType(XMLLanguage.INSTANCE) {
  override fun doParseContents(chameleon: ASTNode, psi: PsiElement): ASTNode {
    val factory = PsiSyntaxBuilderFactory.getInstance()
    val builder = factory.createBuilder(
      chameleon = chameleon,
      lang = language,
      text = chameleon.chars,
    )
    val syntaxTreeBuilder = builder.getSyntaxTreeBuilder()

    val startTime = System.nanoTime()
    syntaxTreeBuilder.enforceCommentTokens(emptySyntaxElementTypeSet())
    builder.setCustomComparator(REPARSE_XML_TAG_BY_NAME)
    XmlParser().parse(syntaxTreeBuilder)
    val node = builder.getTreeBuilt()
    registerParse(builder, XMLLanguage.INSTANCE, System.nanoTime() - startTime)
    return node.getFirstChildNode()
  }
}