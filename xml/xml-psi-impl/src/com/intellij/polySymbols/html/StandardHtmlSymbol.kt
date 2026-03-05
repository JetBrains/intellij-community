// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.html

import com.intellij.documentation.mdn.MdnDocumentedSymbol
import com.intellij.model.Pointer
import com.intellij.openapi.project.Project
import com.intellij.polySymbols.query.PolySymbolScope
import com.intellij.polySymbols.search.PsiSourcedPolySymbol
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class StandardHtmlSymbol : MdnDocumentedSymbol(), PsiSourcedPolySymbol, PolySymbolScope {

  abstract val project: Project?

  abstract override fun createPointer(): Pointer<out StandardHtmlSymbol>
}