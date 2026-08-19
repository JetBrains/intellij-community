// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.highlighter

import com.intellij.lexer.HtmlLexer
import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.XmlHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.tree.IElementType
import com.intellij.psi.xml.XmlTokenType.TAG_WHITE_SPACE
import com.intellij.psi.xml.XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER
import com.intellij.psi.xml.XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER
import com.intellij.psi.xml.XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN
import com.intellij.psi.xml.XmlTokenType.XML_BAD_CHARACTER
import com.intellij.psi.xml.XmlTokenType.XML_CHAR_ENTITY_REF
import com.intellij.psi.xml.XmlTokenType.XML_COMMENT_CHARACTERS
import com.intellij.psi.xml.XmlTokenType.XML_COMMENT_END
import com.intellij.psi.xml.XmlTokenType.XML_COMMENT_START
import com.intellij.psi.xml.XmlTokenType.XML_CONDITIONAL_COMMENT_END
import com.intellij.psi.xml.XmlTokenType.XML_CONDITIONAL_COMMENT_END_START
import com.intellij.psi.xml.XmlTokenType.XML_CONDITIONAL_COMMENT_START
import com.intellij.psi.xml.XmlTokenType.XML_CONDITIONAL_COMMENT_START_END
import com.intellij.psi.xml.XmlTokenType.XML_DOCTYPE_END
import com.intellij.psi.xml.XmlTokenType.XML_DOCTYPE_PUBLIC
import com.intellij.psi.xml.XmlTokenType.XML_DOCTYPE_START
import com.intellij.psi.xml.XmlTokenType.XML_EMPTY_ELEMENT_END
import com.intellij.psi.xml.XmlTokenType.XML_END_TAG_START
import com.intellij.psi.xml.XmlTokenType.XML_ENTITY_REF_TOKEN
import com.intellij.psi.xml.XmlTokenType.XML_EQ
import com.intellij.psi.xml.XmlTokenType.XML_NAME
import com.intellij.psi.xml.XmlTokenType.XML_PI_END
import com.intellij.psi.xml.XmlTokenType.XML_PI_START
import com.intellij.psi.xml.XmlTokenType.XML_PI_TARGET
import com.intellij.psi.xml.XmlTokenType.XML_START_TAG_START
import com.intellij.psi.xml.XmlTokenType.XML_TAG_CHARACTERS
import com.intellij.psi.xml.XmlTokenType.XML_TAG_END
import com.intellij.psi.xml.XmlTokenType.XML_TAG_NAME
import com.intellij.util.containers.MultiMap

open class HtmlFileHighlighter : SyntaxHighlighterBase() {
  override fun getHighlightingLexer(): Lexer {
    return HtmlLexer(true)
  }

  override fun getTokenHighlights(tokenType: IElementType): Array<out TextAttributesKey> {
    return Holder.ourMap.getOrDefault(tokenType, Holder.HTML_CODE_ARRAY)
  }

  private object Holder {
    @JvmStatic
    val ourMap: XmlFileHighlighter.AttributeArrayMapStorage
    val HTML_CODE_ARRAY: Array<out TextAttributesKey> = pack(XmlHighlighterColors.HTML_CODE)
    init {
      val ourMap: MultiMap<IElementType, TextAttributesKey> = MultiMap.create()
      ourMap.putValue(XML_TAG_CHARACTERS, XmlHighlighterColors.HTML_TAG)

      for (type in sequenceOf(XML_COMMENT_START, XML_COMMENT_END, XML_COMMENT_CHARACTERS,
                              XML_CONDITIONAL_COMMENT_END, XML_CONDITIONAL_COMMENT_END_START,
                              XML_CONDITIONAL_COMMENT_START, XML_CONDITIONAL_COMMENT_START_END)) {
        ourMap.putValue(type, XmlHighlighterColors.HTML_COMMENT)
      }

      for (type in sequenceOf(XML_START_TAG_START, XML_END_TAG_START, XML_TAG_END, XML_EMPTY_ELEMENT_END,
                              TAG_WHITE_SPACE)) {
        ourMap.putValue(type, XmlHighlighterColors.HTML_TAG)
      }

      ourMap.putValues(XML_TAG_NAME, listOf(XmlHighlighterColors.HTML_TAG, XmlHighlighterColors.HTML_TAG_NAME))
      ourMap.putValues(XML_NAME, listOf(XmlHighlighterColors.HTML_TAG, XmlHighlighterColors.HTML_ATTRIBUTE_NAME))
      for (type in sequenceOf(XML_EQ,
                              XML_ATTRIBUTE_VALUE_TOKEN, XML_ATTRIBUTE_VALUE_START_DELIMITER,
                              XML_ATTRIBUTE_VALUE_END_DELIMITER)) {
        ourMap.putValues(type, listOf(XmlHighlighterColors.HTML_TAG, XmlHighlighterColors.HTML_ATTRIBUTE_VALUE))
      }

      for (type in sequenceOf(XML_PI_START, XML_PI_END, XML_DOCTYPE_START, XML_DOCTYPE_END, XML_DOCTYPE_PUBLIC)) {
        ourMap.putValue(type, XmlHighlighterColors.HTML_TAG)
      }

      ourMap.putValues(XML_PI_TARGET, listOf(XmlHighlighterColors.HTML_TAG, XmlHighlighterColors.HTML_TAG_NAME))

      ourMap.putValue(XML_CHAR_ENTITY_REF, XmlHighlighterColors.HTML_ENTITY_REFERENCE)
      ourMap.putValue(XML_ENTITY_REF_TOKEN, XmlHighlighterColors.HTML_ENTITY_REFERENCE)

      ourMap.putValue(XML_BAD_CHARACTER, HighlighterColors.BAD_CHARACTER)
      this.ourMap = XmlFileHighlighter.createMapAndListenForExtensionChanges(ourMap) {
          attributes -> pack(XmlHighlighterColors.HTML_CODE, attributes)
      }
    }
  }
}
