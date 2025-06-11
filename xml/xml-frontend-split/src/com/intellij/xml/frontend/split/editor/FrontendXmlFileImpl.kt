package com.intellij.xml.frontend.split.editor

import com.intellij.psi.FileViewProvider
import com.intellij.psi.tree.IElementType

class FrontendXmlFileImpl(
  viewProvider: FileViewProvider,
  elementType: IElementType,
) : FrontendFileImpl(viewProvider, elementType) {

  override fun toString(): String =
    "XmlFile:$name"
}
