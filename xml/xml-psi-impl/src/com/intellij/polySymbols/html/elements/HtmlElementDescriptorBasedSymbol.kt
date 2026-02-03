// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.html.elements

import com.intellij.documentation.mdn.MdnSymbolDocumentation
import com.intellij.documentation.mdn.getHtmlApiNamespace
import com.intellij.documentation.mdn.getHtmlMdnTagDocumentation
import com.intellij.model.Pointer
import com.intellij.openapi.project.Project
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolKind
import com.intellij.polySymbols.html.HTML_ELEMENTS
import com.intellij.polySymbols.html.StandardHtmlSymbol
import com.intellij.psi.PsiElement
import com.intellij.psi.createSmartPointer
import com.intellij.psi.html.HtmlTag
import com.intellij.xml.XmlElementDescriptor

fun XmlElementDescriptor.asHtmlSymbol(tag: HtmlTag?): StandardHtmlSymbol =
  HtmlElementDescriptorBasedSymbol(this, tag)

internal class HtmlElementDescriptorBasedSymbol(
  val descriptor: XmlElementDescriptor,
  private val tag: HtmlTag?,
) : PolySymbol, StandardHtmlSymbol() {

  override val project: Project?
    get() = tag?.project ?: descriptor.declaration?.project

  override fun getMdnDocumentation(): MdnSymbolDocumentation? =
    getHtmlMdnTagDocumentation(getHtmlApiNamespace(descriptor.nsDescriptor?.name, tag, name),
                               name)

  override val kind: PolySymbolKind
    get() = HTML_ELEMENTS

  override val name: String = descriptor.name

  override val priority: PolySymbol.Priority
    get() = PolySymbol.Priority.LOW

  override val defaultValue: String?
    get() = descriptor.defaultValue

  override val source: PsiElement?
    get() = descriptor.declaration

  override fun createPointer(): Pointer<HtmlElementDescriptorBasedSymbol> {
    val descriptor = this.descriptor
    val tagPtr = tag?.createSmartPointer()

    return Pointer<HtmlElementDescriptorBasedSymbol> {
      val tag = tagPtr?.let { it.dereference() ?: return@Pointer null }
      HtmlElementDescriptorBasedSymbol(descriptor, tag)
    }
  }

  override fun equals(other: Any?): Boolean =
    other === this ||
    other is HtmlElementDescriptorBasedSymbol
    && other.descriptor == this.descriptor

  override fun hashCode(): Int =
    descriptor.hashCode()
}