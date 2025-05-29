// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.documentation.mdn

import com.intellij.psi.PsiElement
import com.intellij.webSymbols.PolySymbol
import com.intellij.webSymbols.PolySymbolApiStatus
import com.intellij.webSymbols.documentation.WebSymbolDocumentation

abstract class MdnDocumentedSymbol : PolySymbol {

  private val mdnDoc by lazy(LazyThreadSafetyMode.PUBLICATION) {
    getMdnDocumentation()
  }

  protected abstract fun getMdnDocumentation(): MdnSymbolDocumentation?

  override val apiStatus: PolySymbolApiStatus
    get() = mdnDoc?.apiStatus ?: PolySymbolApiStatus.Stable

  override val description: String?
    get() = mdnDoc?.description

  override val docUrl: String?
    get() = mdnDoc?.url

  override val descriptionSections: Map<String, String>
    get() = mdnDoc?.sections ?: emptyMap()

  override fun createDocumentation(location: PsiElement?): WebSymbolDocumentation? =
    this.mdnDoc?.let { mdnDoc ->
      val documentation = super.createDocumentation(location)
      return documentation?.with(
        apiStatus = null, // already contained in MDN documentation sections
        footnote = mdnDoc.footnote
                     ?.let { it + (documentation.footnote?.let { prev -> "<br>$prev" } ?: "") }
                   ?: documentation.footnote,
      )
    } ?: super.createDocumentation(location)

}
