package com.intellij.xml.frontend.split.editor

import com.intellij.psi.FileViewProvider
import com.intellij.psi.xml.XmlElementType

class FrontendHtmlFileImpl(
  viewProvider: FileViewProvider,
) : FrontendFileImpl(viewProvider, XmlElementType.HTML_FILE) {

  override fun toString(): String =
    "HtmlFile:$name"
}
