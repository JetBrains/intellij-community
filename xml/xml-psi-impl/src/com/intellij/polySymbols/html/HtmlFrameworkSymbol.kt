// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.html

import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.context.PolyContext
import com.intellij.polySymbols.framework.FrameworkId
import com.intellij.polySymbols.framework.framework
import com.intellij.polySymbols.html.HtmlFrameworkSymbolsSupport.HtmlFrameworkIdProperty
import com.intellij.polySymbols.utils.unwrapMatchedSymbols
import com.intellij.polySymbols.webTypes.WebTypesSymbol

val PolySymbol.framework: FrameworkId?
  get() =
    when (this) {
      is HtmlFrameworkSymbol -> this.framework
      else -> unwrapMatchedSymbols().firstNotNullOfOrNull { (it as? WebTypesSymbol)?.origin?.framework }
              ?: this[HtmlFrameworkIdProperty]
    }

interface HtmlFrameworkSymbol : PolySymbol {

  @PolySymbol.Property(HtmlFrameworkIdProperty::class)
  val framework: FrameworkId?

  override fun matchContext(context: PolyContext): Boolean =
    super.matchContext(context)
    && (framework == null || context.framework.let { it == null || it == framework })

}
