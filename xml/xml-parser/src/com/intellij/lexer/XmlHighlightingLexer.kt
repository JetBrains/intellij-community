// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lexer

import com.intellij.psi.tree.IElementType
import com.intellij.psi.xml.XmlTokenType

class XmlHighlightingLexer :
  DelegateLexer(createXmlLexer()) {

  override fun getTokenType(): IElementType? {
    var tokenType = delegate.tokenType
                    ?: return null

    tokenType = fixWrongTokenTypes(tokenType)

    return tokenType
  }

  private fun fixWrongTokenTypes(
    tokenType: IElementType,
  ): IElementType {
    val state = state and 0x1f // __XmlLexer.C_COMMENT_END is last state with value 30

    if (tokenType === XmlTokenType.XML_NAME) {
      if (state == __XmlLexer.TAG || state == __XmlLexer.END_TAG) {
        // translate XML names for tags into XmlTagName
        return XmlTokenType.XML_TAG_NAME
      }
    }
    else if (tokenType === XmlTokenType.XML_WHITE_SPACE) {
      return when (state) {
        __XmlLexer.ATTR_LIST,
        __XmlLexer.ATTR,
          -> XmlTokenType.TAG_WHITE_SPACE

        else -> XmlTokenType.XML_REAL_WHITE_SPACE
      }
    }
    else if (tokenType === XmlTokenType.XML_CHAR_ENTITY_REF
             || tokenType === XmlTokenType.XML_ENTITY_REF_TOKEN
    ) {
      if (state == __XmlLexer.COMMENT)
        return XmlTokenType.XML_COMMENT_CHARACTERS
    }

    return tokenType
  }
}
