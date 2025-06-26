// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.documentation.mdn

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolApiStatus
import com.intellij.polySymbols.documentation.PolySymbolDocumentationTarget
import com.intellij.psi.PsiElement

abstract class MdnDocumentedSymbol : PolySymbol {

  private val mdnDoc by lazy(LazyThreadSafetyMode.PUBLICATION) {
    getMdnDocumentation()
  }

  protected abstract fun getMdnDocumentation(): MdnSymbolDocumentation?

  override fun getDocumentationTarget(location: PsiElement?): DocumentationTarget? =
    PolySymbolDocumentationTarget.create(this, location) { symbol, location ->
      val mdnDoc = symbol.mdnDoc ?: return@create
      description = mdnDoc.description
      docUrl = mdnDoc.url
      descriptionSections.putAll(mdnDoc.sections)
      footnote = mdnDoc.footnote
      defaultValue = symbol.defaultValue

      // already contained in MDN documentation sections
      apiStatus = null
    }

  open val defaultValue: @NlsSafe String? get() = null

  override val apiStatus: PolySymbolApiStatus
    get() = mdnDoc?.apiStatus ?: PolySymbolApiStatus.Stable

}
