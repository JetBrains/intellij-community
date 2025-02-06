// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.xml

import com.intellij.lang.xml.XMLLanguage
import com.intellij.psi.tree.IElementType

// TODO: remove after `XmlElementType` moving
object BasicXmlElementType {
  @JvmField
  val XML_MARKUP_DECL: IElementType =
    getFieldOrFallback("XML_MARKUP_DECL", XMLLanguage.INSTANCE)
}

private fun getFieldOrFallback(
  name: String,
  language: XMLLanguage,
): IElementType {
  val clazz = try {
    Class.forName("com.intellij.psi.xml.XmlElementType")
  }
  catch (e: ClassNotFoundException) {
    return IElementType(name, language)
  }

  return clazz
    .getDeclaredField(name)
    .get(null) as IElementType
}