// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.xml

import com.intellij.html.embedding.HtmlRawTextElementType
import com.intellij.lang.dtd.DTDLanguage
import com.intellij.lang.xhtml.XHTMLLanguage
import com.intellij.lang.xml.XMLLanguage
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.xml.IXmlElementType

object XmlElementType {
  @JvmField
  val XML_DOCUMENT: IElementType =
    IXmlElementType("XML_DOCUMENT")

  @JvmField
  val XML_PROLOG: IElementType =
    IXmlElementType("XML_PROLOG")

  @JvmField
  val XML_DECL: IElementType =
    IXmlElementType("XML_DECL")

  @JvmField
  val XML_DOCTYPE: IElementType =
    IXmlElementType("XML_DOCTYPE")

  @JvmField
  val XML_ATTRIBUTE: IElementType =
    XmlAttributeElementType()

  @JvmField
  val XML_COMMENT: IElementType =
    IXmlElementType("XML_COMMENT")

  @JvmField
  val XML_TAG: IElementType =
    XmlTagElementType("XML_TAG")

  @JvmField
  val XML_ELEMENT_DECL: IElementType =
    IXmlElementType("XML_ELEMENT_DECL")

  @JvmField
  val XML_CONDITIONAL_SECTION: IElementType =
    IXmlElementType("XML_CONDITIONAL_SECTION")

  @JvmField
  val XML_ATTLIST_DECL: IElementType =
    IXmlElementType("XML_ATTLIST_DECL")

  @JvmField
  val XML_NOTATION_DECL: IElementType =
    IXmlElementType("XML_NOTATION_DECL")

  @JvmField
  val XML_ENTITY_DECL: IElementType =
    IXmlElementType("XML_ENTITY_DECL")

  @JvmField
  val XML_ELEMENT_CONTENT_SPEC: IElementType =
    IXmlElementType("XML_ELEMENT_CONTENT_SPEC")

  @JvmField
  val XML_ELEMENT_CONTENT_GROUP: IElementType =
    IXmlElementType("XML_ELEMENT_CONTENT_GROUP")

  @JvmField
  val XML_ATTRIBUTE_DECL: IElementType =
    IXmlElementType("XML_ATTRIBUTE_DECL")

  @JvmField
  val XML_ATTRIBUTE_VALUE: IElementType =
    IXmlElementType("XML_ATTRIBUTE_VALUE")

  @JvmField
  val XML_ENTITY_REF: IElementType =
    IXmlElementType("XML_ENTITY_REF")

  @JvmField
  val XML_ENUMERATED_TYPE: IElementType =
    IXmlElementType("XML_ENUMERATED_TYPE")

  @JvmField
  val XML_PROCESSING_INSTRUCTION: IElementType =
    IXmlElementType("XML_PROCESSING_INSTRUCTION")

  @JvmField
  val XML_CDATA: IElementType =
    IXmlElementType("XML_CDATA")

  //todo: move to html
  @JvmField
  val HTML_DOCUMENT: IElementType =
    IXmlElementType("HTML_DOCUMENT")

  @JvmField
  val HTML_TAG: IElementType =
    XmlTagElementType("HTML_TAG")

  @JvmField
  val HTML_FILE: IFileElementType =
    HtmlFileElementType.INSTANCE

  @JvmField
  val HTML_EMBEDDED_CONTENT: IElementType =
    EmbeddedHtmlContentElementType()

  @JvmField
  val HTML_RAW_TEXT: IElementType =
    HtmlRawTextElementType

  @JvmField
  val XML_TEXT: IElementType =
    XmlTextElementType()

  @JvmField
  val XML_FILE: IFileElementType =
    XmlFileElementType()

  @JvmField
  val XHTML_FILE: IElementType =
    IFileElementType(XHTMLLanguage)


  @JvmField
  val DTD_FILE: IFileElementType =
    IFileElementType("DTD_FILE", DTDLanguage)

  @JvmField
  val XML_MARKUP_DECL: IElementType =
    XmlMarkupDeclElementType()
}
