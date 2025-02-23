/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.xml

import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.xml.IXmlLeafElementType

/**
 * Specifies XML token types.
 */
object XmlTokenType {
  @JvmField
  val XML_START_TAG_START: IElementType =
    IXmlLeafElementType("XML_START_TAG_START")

  @JvmField
  val XML_END_TAG_START: IElementType =
    IXmlLeafElementType("XML_END_TAG_START")

  @JvmField
  val XML_TAG_END: IElementType =
    IXmlLeafElementType("XML_TAG_END")

  @JvmField
  val XML_EMPTY_ELEMENT_END: IElementType =
    IXmlLeafElementType("XML_EMPTY_ELEMENT_END")

  @JvmField
  val XML_TAG_NAME: IElementType =
    IXmlLeafElementType("XML_TAG_NAME")

  @JvmField
  val XML_NAME: IElementType =
    IXmlLeafElementType("XML_NAME")

  @JvmField
  val XML_ATTRIBUTE_VALUE_TOKEN: IElementType =
    IXmlLeafElementType("XML_ATTRIBUTE_VALUE_TOKEN")

  @JvmField
  val XML_ATTRIBUTE_VALUE_START_DELIMITER: IElementType =
    IXmlLeafElementType("XML_ATTRIBUTE_VALUE_START_DELIMITER")

  @JvmField
  val XML_ATTRIBUTE_VALUE_END_DELIMITER: IElementType =
    IXmlLeafElementType("XML_ATTRIBUTE_VALUE_END_DELIMITER")

  @JvmField
  val XML_EQ: IElementType =
    IXmlLeafElementType("XML_EQ")

  @JvmField
  val XML_DATA_CHARACTERS: IElementType =
    IXmlLeafElementType("XML_DATA_CHARACTERS")

  @JvmField
  val XML_TAG_CHARACTERS: IElementType =
    IXmlLeafElementType("XML_TAG_CHARACTERS")

  @JvmField
  val XML_WHITE_SPACE: IElementType =
    TokenType.WHITE_SPACE

  @JvmField
  val XML_REAL_WHITE_SPACE: IElementType =
    IXmlLeafElementType("XML_WHITE_SPACE")

  @JvmField
  val XML_COMMENT_START: IElementType =
    IXmlLeafElementType("XML_COMMENT_START")

  @JvmField
  val XML_COMMENT_END: IElementType =
    IXmlLeafElementType("XML_COMMENT_END")

  @JvmField
  val XML_COMMENT_CHARACTERS: IElementType =
    IXmlLeafElementType("XML_COMMENT_CHARACTERS")

  @JvmField
  val XML_DECL_START: IElementType =
    IXmlLeafElementType("XML_DECL_START")

  @JvmField
  val XML_DECL_END: IElementType =
    IXmlLeafElementType("XML_DECL_END")

  @JvmField
  val XML_DOCTYPE_START: IElementType =
    IXmlLeafElementType("XML_DOCTYPE_START")

  @JvmField
  val XML_DOCTYPE_END: IElementType =
    IXmlLeafElementType("XML_DOCTYPE_END")

  @JvmField
  val XML_DOCTYPE_SYSTEM: IElementType =
    IXmlLeafElementType("XML_DOCTYPE_SYSTEM")

  @JvmField
  val XML_DOCTYPE_PUBLIC: IElementType =
    IXmlLeafElementType("XML_DOCTYPE_PUBLIC")

  @JvmField
  val XML_MARKUP_START: IElementType =
    IXmlLeafElementType("XML_MARKUP_START")

  @JvmField
  val XML_MARKUP_END: IElementType =
    IXmlLeafElementType("XML_MARKUP_END")

  @JvmField
  val XML_CDATA_START: IElementType =
    IXmlLeafElementType("XML_CDATA_START")

  @JvmField
  val XML_CONDITIONAL_SECTION_START: IElementType =
    IXmlLeafElementType("XML_CONDITIONAL_SECTION_START")

  @JvmField
  val XML_CONDITIONAL_INCLUDE: IElementType =
    IXmlLeafElementType("XML_CONDITIONAL_INCLUDE")

  @JvmField
  val XML_CONDITIONAL_IGNORE: IElementType =
    IXmlLeafElementType("XML_CONDITIONAL_IGNORE")

  @JvmField
  val XML_CDATA_END: IElementType =
    IXmlLeafElementType("XML_CDATA_END")

  @JvmField
  val XML_CONDITIONAL_SECTION_END: IElementType =
    IXmlLeafElementType("XML_CONDITIONAL_SECTION_END")

  @JvmField
  val XML_ELEMENT_DECL_START: IElementType =
    IXmlLeafElementType("XML_ELEMENT_DECL_START")

  @JvmField
  val XML_NOTATION_DECL_START: IElementType =
    IXmlLeafElementType("XML_NOTATION_DECL_START")

  @JvmField
  val XML_ATTLIST_DECL_START: IElementType =
    IXmlLeafElementType("XML_ATTLIST_DECL_START")

  @JvmField
  val XML_ENTITY_DECL_START: IElementType =
    IXmlLeafElementType("XML_ENTITY_DECL_START")

  @JvmField
  val XML_PCDATA: IElementType =
    IXmlLeafElementType("XML_PCDATA")

  @JvmField
  val XML_LEFT_PAREN: IElementType =
    IXmlLeafElementType("XML_LEFT_PAREN")

  @JvmField
  val XML_RIGHT_PAREN: IElementType =
    IXmlLeafElementType("XML_RIGHT_PAREN")

  @JvmField
  val XML_CONTENT_EMPTY: IElementType =
    IXmlLeafElementType("XML_CONTENT_EMPTY")

  @JvmField
  val XML_CONTENT_ANY: IElementType =
    IXmlLeafElementType("XML_CONTENT_ANY")

  @JvmField
  val XML_QUESTION: IElementType =
    IXmlLeafElementType("XML_QUESTION")

  @JvmField
  val XML_STAR: IElementType =
    IXmlLeafElementType("XML_STAR")

  @JvmField
  val XML_PLUS: IElementType =
    IXmlLeafElementType("XML_PLUS")

  @JvmField
  val XML_BAR: IElementType =
    IXmlLeafElementType("XML_BAR")

  @JvmField
  val XML_COMMA: IElementType =
    IXmlLeafElementType("XML_COMMA")

  @JvmField
  val XML_AMP: IElementType =
    IXmlLeafElementType("XML_AMP")

  @JvmField
  val XML_SEMI: IElementType =
    IXmlLeafElementType("XML_SEMI")

  @JvmField
  val XML_PERCENT: IElementType =
    IXmlLeafElementType("XML_PERCENT")

  @JvmField
  val XML_ATT_IMPLIED: IElementType =
    IXmlLeafElementType("XML_ATT_IMPLIED")

  @JvmField
  val XML_ATT_REQUIRED: IElementType =
    IXmlLeafElementType("XML_ATT_REQUIRED")

  @JvmField
  val XML_ATT_FIXED: IElementType =
    IXmlLeafElementType("XML_ATT_FIXED")

  @JvmField
  val XML_ENTITY_REF_TOKEN: IElementType =
    IXmlLeafElementType("XML_ENTITY_REF_TOKEN")

  @JvmField
  val TAG_WHITE_SPACE: IElementType =
    IXmlLeafElementType("TAG_WHITE_SPACE")

  @JvmField
  val XML_PI_START: IElementType =
    IXmlLeafElementType("XML_PI_START")

  @JvmField
  val XML_PI_END: IElementType =
    IXmlLeafElementType("XML_PI_END")

  @JvmField
  val XML_PI_TARGET: IElementType =
    IXmlLeafElementType("XML_PI_TARGET")

  @JvmField
  val XML_CHAR_ENTITY_REF: IElementType =
    IXmlLeafElementType("XML_CHAR_ENTITY_REF")

  @JvmField
  val XML_BAD_CHARACTER: IElementType =
    IXmlLeafElementType("XML_BAD_CHARACTER")

  @JvmField
  val XML_CONDITIONAL_COMMENT_START: IElementType =
    IXmlLeafElementType("CONDITIONAL_COMMENT_START")

  @JvmField
  val XML_CONDITIONAL_COMMENT_START_END: IElementType =
    IXmlLeafElementType("CONDITIONAL_COMMENT_START_END")

  @JvmField
  val XML_CONDITIONAL_COMMENT_END_START: IElementType =
    IXmlLeafElementType("CONDITIONAL_COMMENT_END_START")

  @JvmField
  val XML_CONDITIONAL_COMMENT_END: IElementType =
    IXmlLeafElementType("CONDITIONAL_COMMENT_END")

  @JvmField
  val COMMENTS: TokenSet = TokenSet.create(
    XML_COMMENT_START,
    XML_COMMENT_CHARACTERS,
    XML_COMMENT_END,
  )

  @JvmField
  val WHITESPACES: TokenSet = TokenSet.create(
    XML_WHITE_SPACE,
  )
}
