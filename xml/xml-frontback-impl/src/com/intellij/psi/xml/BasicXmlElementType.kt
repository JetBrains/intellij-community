package com.intellij.psi.xml

import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.xml.IXmlElementType
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Deprecated("Remove after `XmlElementType` moving to `xml-frontback-impl` module")
interface BasicXmlElementType {
  companion object {
    @JvmField
    val XML_ATTRIBUTE_VALUE: IElementType = IXmlElementType("XML_ATTRIBUTE_VALUE")

    @JvmField
    val XML_TEXT: IElementType = XmlTextElementType()
  }
}
