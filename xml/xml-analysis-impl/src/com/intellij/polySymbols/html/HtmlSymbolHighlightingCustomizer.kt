// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.html

import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.openapi.editor.XmlHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.polySymbols.PolySymbolQualifiedKind
import com.intellij.polySymbols.highlighting.PolySymbolHighlightingCustomizer
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlTag

class HtmlSymbolHighlightingCustomizer : PolySymbolHighlightingCustomizer {
  override fun getSymbolKindTextAttributes(qualifiedKind: PolySymbolQualifiedKind): TextAttributesKey? =
    when (qualifiedKind) {
      HTML_ATTRIBUTES -> XmlHighlighterColors.HTML_ATTRIBUTE_NAME
      HTML_ELEMENTS -> XmlHighlighterColors.HTML_TAG_NAME
      HTML_ATTRIBUTE_VALUES -> XmlHighlighterColors.HTML_ATTRIBUTE_VALUE
      else -> null
    }

  override fun getDefaultHostClassTextAttributes(): Map<Class<out PsiExternalReferenceHost>, TextAttributesKey> =
    mapOf(
      XmlAttribute::class.java to XmlHighlighterColors.HTML_ATTRIBUTE_NAME,
      XmlTag::class.java to XmlHighlighterColors.HTML_TAG_NAME,
      XmlAttributeValue::class.java to XmlHighlighterColors.HTML_ATTRIBUTE_VALUE,
    )

}