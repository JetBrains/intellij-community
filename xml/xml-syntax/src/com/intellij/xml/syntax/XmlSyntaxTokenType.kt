// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.syntax

import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.SyntaxElementTypeSet
import com.intellij.platform.syntax.element.SyntaxTokenTypes
import com.intellij.platform.syntax.syntaxElementTypeSetOf

/**
 * Specifies XML token types.
 */
object XmlSyntaxTokenType {
  @JvmField val XML_START_TAG_START: SyntaxElementType = SyntaxElementType("XML_START_TAG_START")
  @JvmField val XML_END_TAG_START: SyntaxElementType = SyntaxElementType("XML_END_TAG_START")
  @JvmField val XML_TAG_END: SyntaxElementType = SyntaxElementType("XML_TAG_END")
  @JvmField val XML_EMPTY_ELEMENT_END: SyntaxElementType = SyntaxElementType("XML_EMPTY_ELEMENT_END")
  @JvmField val XML_TAG_NAME: SyntaxElementType = SyntaxElementType("XML_TAG_NAME")
  @JvmField val XML_NAME: SyntaxElementType = SyntaxElementType("XML_NAME")
  @JvmField val XML_ATTRIBUTE_VALUE_TOKEN: SyntaxElementType = SyntaxElementType("XML_ATTRIBUTE_VALUE_TOKEN")
  @JvmField val XML_ATTRIBUTE_VALUE_START_DELIMITER: SyntaxElementType = SyntaxElementType("XML_ATTRIBUTE_VALUE_START_DELIMITER")
  @JvmField val XML_ATTRIBUTE_VALUE_END_DELIMITER: SyntaxElementType = SyntaxElementType("XML_ATTRIBUTE_VALUE_END_DELIMITER")
  @JvmField val XML_EQ: SyntaxElementType = SyntaxElementType("XML_EQ")
  @JvmField val XML_DATA_CHARACTERS: SyntaxElementType = SyntaxElementType("XML_DATA_CHARACTERS")
  @JvmField val XML_TAG_CHARACTERS: SyntaxElementType = SyntaxElementType("XML_TAG_CHARACTERS")
  @JvmField val XML_WHITE_SPACE: SyntaxElementType = SyntaxTokenTypes.WHITE_SPACE
  @JvmField val XML_REAL_WHITE_SPACE: SyntaxElementType = SyntaxElementType("XML_WHITE_SPACE")
  @JvmField val XML_COMMENT_START: SyntaxElementType = SyntaxElementType("XML_COMMENT_START")
  @JvmField val XML_COMMENT_END: SyntaxElementType = SyntaxElementType("XML_COMMENT_END")
  @JvmField val XML_COMMENT_CHARACTERS: SyntaxElementType = SyntaxElementType("XML_COMMENT_CHARACTERS")
  @JvmField val XML_DECL_START: SyntaxElementType = SyntaxElementType("XML_DECL_START")
  @JvmField val XML_DECL_END: SyntaxElementType = SyntaxElementType("XML_DECL_END")
  @JvmField val XML_DOCTYPE_START: SyntaxElementType = SyntaxElementType("XML_DOCTYPE_START")
  @JvmField val XML_DOCTYPE_END: SyntaxElementType = SyntaxElementType("XML_DOCTYPE_END")
  @JvmField val XML_DOCTYPE_SYSTEM: SyntaxElementType = SyntaxElementType("XML_DOCTYPE_SYSTEM")
  @JvmField val XML_DOCTYPE_PUBLIC: SyntaxElementType = SyntaxElementType("XML_DOCTYPE_PUBLIC")
  @JvmField val XML_MARKUP_START: SyntaxElementType = SyntaxElementType("XML_MARKUP_START")
  @JvmField val XML_MARKUP_END: SyntaxElementType = SyntaxElementType("XML_MARKUP_END")
  @JvmField val XML_CDATA_START: SyntaxElementType = SyntaxElementType("XML_CDATA_START")
  @JvmField val XML_CONDITIONAL_SECTION_START: SyntaxElementType = SyntaxElementType("XML_CONDITIONAL_SECTION_START")
  @JvmField val XML_CONDITIONAL_INCLUDE: SyntaxElementType = SyntaxElementType("XML_CONDITIONAL_INCLUDE")
  @JvmField val XML_CONDITIONAL_IGNORE: SyntaxElementType = SyntaxElementType("XML_CONDITIONAL_IGNORE")
  @JvmField val XML_CDATA_END: SyntaxElementType = SyntaxElementType("XML_CDATA_END")
  @JvmField val XML_CONDITIONAL_SECTION_END: SyntaxElementType = SyntaxElementType("XML_CONDITIONAL_SECTION_END")
  @JvmField val XML_ELEMENT_DECL_START: SyntaxElementType = SyntaxElementType("XML_ELEMENT_DECL_START")
  @JvmField val XML_NOTATION_DECL_START: SyntaxElementType = SyntaxElementType("XML_NOTATION_DECL_START")
  @JvmField val XML_ATTLIST_DECL_START: SyntaxElementType = SyntaxElementType("XML_ATTLIST_DECL_START")
  @JvmField val XML_ENTITY_DECL_START: SyntaxElementType = SyntaxElementType("XML_ENTITY_DECL_START")
  @JvmField val XML_PCDATA: SyntaxElementType = SyntaxElementType("XML_PCDATA")
  @JvmField val XML_LEFT_PAREN: SyntaxElementType = SyntaxElementType("XML_LEFT_PAREN")
  @JvmField val XML_RIGHT_PAREN: SyntaxElementType = SyntaxElementType("XML_RIGHT_PAREN")
  @JvmField val XML_CONTENT_EMPTY: SyntaxElementType = SyntaxElementType("XML_CONTENT_EMPTY")
  @JvmField val XML_CONTENT_ANY: SyntaxElementType = SyntaxElementType("XML_CONTENT_ANY")
  @JvmField val XML_QUESTION: SyntaxElementType = SyntaxElementType("XML_QUESTION")
  @JvmField val XML_STAR: SyntaxElementType = SyntaxElementType("XML_STAR")
  @JvmField val XML_PLUS: SyntaxElementType = SyntaxElementType("XML_PLUS")
  @JvmField val XML_BAR: SyntaxElementType = SyntaxElementType("XML_BAR")
  @JvmField val XML_COMMA: SyntaxElementType = SyntaxElementType("XML_COMMA")
  @JvmField val XML_AMP: SyntaxElementType = SyntaxElementType("XML_AMP")
  @JvmField val XML_SEMI: SyntaxElementType = SyntaxElementType("XML_SEMI")
  @JvmField val XML_PERCENT: SyntaxElementType = SyntaxElementType("XML_PERCENT")
  @JvmField val XML_ATT_IMPLIED: SyntaxElementType = SyntaxElementType("XML_ATT_IMPLIED")
  @JvmField val XML_ATT_REQUIRED: SyntaxElementType = SyntaxElementType("XML_ATT_REQUIRED")
  @JvmField val XML_ATT_FIXED: SyntaxElementType = SyntaxElementType("XML_ATT_FIXED")
  @JvmField val XML_ENTITY_REF_TOKEN: SyntaxElementType = SyntaxElementType("XML_ENTITY_REF_TOKEN")
  @JvmField val TAG_WHITE_SPACE: SyntaxElementType = SyntaxElementType("TAG_WHITE_SPACE")
  @JvmField val XML_PI_START: SyntaxElementType = SyntaxElementType("XML_PI_START")
  @JvmField val XML_PI_END: SyntaxElementType = SyntaxElementType("XML_PI_END")
  @JvmField val XML_PI_TARGET: SyntaxElementType = SyntaxElementType("XML_PI_TARGET")
  @JvmField val XML_CHAR_ENTITY_REF: SyntaxElementType = SyntaxElementType("XML_CHAR_ENTITY_REF")
  @JvmField val XML_BAD_CHARACTER: SyntaxElementType = SyntaxElementType("XML_BAD_CHARACTER")
  @JvmField val XML_CONDITIONAL_COMMENT_START: SyntaxElementType = SyntaxElementType("CONDITIONAL_COMMENT_START")
  @JvmField val XML_CONDITIONAL_COMMENT_START_END: SyntaxElementType = SyntaxElementType("CONDITIONAL_COMMENT_START_END")
  @JvmField val XML_CONDITIONAL_COMMENT_END_START: SyntaxElementType = SyntaxElementType("CONDITIONAL_COMMENT_END_START")
  @JvmField val XML_CONDITIONAL_COMMENT_END: SyntaxElementType = SyntaxElementType("CONDITIONAL_COMMENT_END")

  @JvmField val COMMENTS: SyntaxElementTypeSet = syntaxElementTypeSetOf(
    XML_COMMENT_START,
    XML_COMMENT_CHARACTERS,
    XML_COMMENT_END,
  )

  @JvmField val WHITESPACES: SyntaxElementTypeSet = syntaxElementTypeSetOf(XML_WHITE_SPACE)
}
