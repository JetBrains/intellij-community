// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.xml

import com.intellij.platform.syntax.SyntaxElementType

object XmlSyntaxElementType {
  @JvmField
  val XML_DOCUMENT: SyntaxElementType =
    SyntaxElementType("XML_DOCUMENT")

  @JvmField
  val XML_PROLOG: SyntaxElementType =
    SyntaxElementType("XML_PROLOG")

  @JvmField
  val XML_DECL: SyntaxElementType =
    SyntaxElementType("XML_DECL")

  @JvmField
  val XML_DOCTYPE: SyntaxElementType =
    SyntaxElementType("XML_DOCTYPE")

  @JvmField
  val XML_ATTRIBUTE: SyntaxElementType =
    SyntaxElementType("XML_ATTRIBUTE")

  @JvmField
  val XML_COMMENT: SyntaxElementType =
    SyntaxElementType("XML_COMMENT")

  @JvmField
  val XML_TAG: SyntaxElementType =
    SyntaxElementType("XML_TAG")

  @JvmField
  val XML_ELEMENT_DECL: SyntaxElementType =
    SyntaxElementType("XML_ELEMENT_DECL")

  @JvmField
  val XML_CONDITIONAL_SECTION: SyntaxElementType =
    SyntaxElementType("XML_CONDITIONAL_SECTION")

  @JvmField
  val XML_ATTLIST_DECL: SyntaxElementType =
    SyntaxElementType("XML_ATTLIST_DECL")

  @JvmField
  val XML_NOTATION_DECL: SyntaxElementType =
    SyntaxElementType("XML_NOTATION_DECL")

  @JvmField
  val XML_ENTITY_DECL: SyntaxElementType =
    SyntaxElementType("XML_ENTITY_DECL")

  @JvmField
  val XML_ELEMENT_CONTENT_SPEC: SyntaxElementType =
    SyntaxElementType("XML_ELEMENT_CONTENT_SPEC")

  @JvmField
  val XML_ELEMENT_CONTENT_GROUP: SyntaxElementType =
    SyntaxElementType("XML_ELEMENT_CONTENT_GROUP")

  @JvmField
  val XML_ATTRIBUTE_DECL: SyntaxElementType =
    SyntaxElementType("XML_ATTRIBUTE_DECL")

  @JvmField
  val XML_ATTRIBUTE_VALUE: SyntaxElementType =
    SyntaxElementType("XML_ATTRIBUTE_VALUE")

  @JvmField
  val XML_ENTITY_REF: SyntaxElementType =
    SyntaxElementType("XML_ENTITY_REF")

  @JvmField
  val XML_ENUMERATED_TYPE: SyntaxElementType =
    SyntaxElementType("XML_ENUMERATED_TYPE")

  @JvmField
  val XML_PROCESSING_INSTRUCTION: SyntaxElementType =
    SyntaxElementType("XML_PROCESSING_INSTRUCTION")

  @JvmField
  val XML_CDATA: SyntaxElementType =
    SyntaxElementType("XML_CDATA")

  //todo: move to html
  @JvmField
  val HTML_DOCUMENT: SyntaxElementType =
    SyntaxElementType("HTML_DOCUMENT")

  @JvmField
  val HTML_TAG: SyntaxElementType =
    SyntaxElementType("HTML_TAG")

  @JvmField
  val HTML_EMBEDDED_CONTENT: SyntaxElementType =
    SyntaxElementType("HTML_EMBEDDED_CONTENT")

  @JvmField
  val XML_TEXT: SyntaxElementType =
    SyntaxElementType("XML_TEXT")

  @JvmField
  val XML_FILE: SyntaxElementType =
    SyntaxElementType("FILE_XMLLanguage")

  @JvmField
  val XHTML_FILE: SyntaxElementType =
    SyntaxElementType("FILE_XHTMLLanguage")

  @JvmField
  val DTD_FILE: SyntaxElementType =
    SyntaxElementType("DTD_FILE")

  @JvmField
  val XML_MARKUP_DECL: SyntaxElementType =
    SyntaxElementType("XML_MARKUP_DECL")
}
