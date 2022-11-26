// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.documentation.mdn

import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.documentation.WebSymbolDocumentation

abstract class MdnDocumentedSymbol : WebSymbol {

  private val mdnDoc by lazy(LazyThreadSafetyMode.NONE) {
    getMdnDocumentation()
  }

  protected abstract fun getMdnDocumentation(): MdnSymbolDocumentation?

  override val deprecated: Boolean
    get() = mdnDoc?.isDeprecated ?: false

  override val experimental: Boolean
    get() = mdnDoc?.isExperimental ?: false

  override val description: String?
    get() = mdnDoc?.description

  override val docUrl: String?
    get() = mdnDoc?.url

  override val descriptionSections: Map<String, String>
    get() = mdnDoc?.sections ?: emptyMap()

  override val documentation: WebSymbolDocumentation?
    get() = this.mdnDoc?.let { mdnDoc ->
      val documentation = super.documentation
      return documentation?.with(
        deprecated = false, // already contained in MDN documentation sections
        experimental = false, // already contained in MDN documentation sections
        footnote = mdnDoc.footnote
                     ?.let { it + (documentation.footnote?.let { prev -> "<br>$prev" } ?: "") }
                   ?: documentation.footnote,
      )
    } ?: super.documentation

}
