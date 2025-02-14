// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.xml

import com.intellij.html.embedding.HtmlRawTextElementType
import com.intellij.lang.html.HTMLParserDefinition
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType

private object XmlElementTypeImpl {
  @JvmField
  val HTML_FILE: IFileElementType =
    HTMLParserDefinition.FILE_ELEMENT_TYPE

  @JvmField
  val HTML_RAW_TEXT: IElementType =
    HtmlRawTextElementType
}
