// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.html

import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolProperty
import com.intellij.polySymbols.context.PolyContext
import com.intellij.polySymbols.framework.FrameworkId
import com.intellij.polySymbols.html.HtmlFrameworkSymbolsSupport.Companion.PROP_HTML_FRAMEWORK_ID
import com.intellij.polySymbols.webTypes.WebTypesJsonOrigin
import com.intellij.polySymbols.webTypes.WebTypesSymbol
import com.intellij.util.asSafely

val PolySymbol.framework: FrameworkId?
  get() =
    when (this) {
      is HtmlFrameworkSymbol -> this.framework
      else -> this.origin.asSafely<WebTypesJsonOrigin>()?.framework
              ?: this[PROP_HTML_FRAMEWORK_ID]
    }

interface HtmlFrameworkSymbol : PolySymbol {

  val framework: FrameworkId?

  override fun <T : Any> get(property: PolySymbolProperty<T>): T? =
    when (property) {
      PROP_HTML_FRAMEWORK_ID -> property.tryCast(framework)
      else -> super.get(property)
    }

  override fun matchContext(context: PolyContext): Boolean =
    super.matchContext(context)
    && (framework == null || context.framework.let { it == null || it == framework })

}
