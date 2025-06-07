// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.xml

import com.intellij.lang.ASTNode
import com.intellij.lang.xml.XMLLanguage
import com.intellij.psi.impl.source.parsing.xml.DtdParsing
import com.intellij.psi.tree.CustomParsingType
import com.intellij.util.CharTable

internal class XmlMarkupDeclElementType :
  CustomParsingType("XML_MARKUP_DECL", XMLLanguage.INSTANCE) {
  override fun parse(text: CharSequence, table: CharTable): ASTNode {
    return DtdParsing(text, XmlElementType.XML_MARKUP_DECL, DtdParsing.TYPE_FOR_MARKUP_DECL, null).parse()
  }
}
