// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.xhtml

import com.intellij.lang.xml.XMLParserDefinition
import com.intellij.lexer.Lexer
import com.intellij.lexer.XHtmlLexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.xml.XmlFileImpl
import com.intellij.psi.xml.XmlElementType

class XHTMLParserDefinition :
  XMLParserDefinition() {

  override fun createLexer(project: Project?): Lexer =
    XHtmlLexer()

  override fun createFile(viewProvider: FileViewProvider): PsiFile {
    return XmlFileImpl(viewProvider, XmlElementType.XHTML_FILE)
  }
}
