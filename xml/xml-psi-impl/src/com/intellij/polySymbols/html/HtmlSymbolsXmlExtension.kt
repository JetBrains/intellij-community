// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.html

import com.intellij.polySymbols.html.attributes.HtmlAttributeSymbolDescriptor
import com.intellij.lang.html.HTMLLanguage
import com.intellij.openapi.util.TextRange
import com.intellij.polySymbols.utils.nameSegments
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.xml.SchemaPrefix
import com.intellij.psi.xml.XmlTag
import com.intellij.xml.HtmlXmlExtension

open class HtmlSymbolsXmlExtension : HtmlXmlExtension() {

  override fun isAvailable(file: PsiFile?): Boolean =
    file?.let {
      // Support extension in plain HTML, PHP, Twig and others
      HTMLLanguage.INSTANCE in it.viewProvider.languages
    } == true

  override fun isRequiredAttributeImplicitlyPresent(tag: XmlTag?, attrName: String?): Boolean {
    if (tag == null || attrName == null) return false
    return tag.attributes.asSequence()
      .map { it.descriptor }
      .filterIsInstance<HtmlAttributeSymbolDescriptor>()
      .map { it.symbol }
      .flatMap { it.nameSegments.asSequence().filter { segment -> segment.problem == null } }
      .flatMap { it.symbols }
      .any { it.name == attrName && it.qualifiedKind == HTML_ATTRIBUTES }
  }

  override fun getPrefixDeclaration(context: XmlTag, namespacePrefix: String?): SchemaPrefix? {
    if (namespacePrefix != null) {
      context.attributes
        .find { it.name.startsWith("$namespacePrefix:") }
        ?.takeIf { it.descriptor is HtmlAttributeSymbolDescriptor }
        ?.let { return SchemaPrefix(it, TextRange.create(0, namespacePrefix.length), namespacePrefix) }
    }
    return super.getPrefixDeclaration(context, namespacePrefix)
  }
}