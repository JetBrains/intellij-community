// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.lang.psi.symbol.identifier

import com.intellij.find.usages.api.SearchTarget
import com.intellij.mermaid.lang.psi.symbol.MermaidSymbol
import com.intellij.model.Pointer

@Suppress("UnstableApiUsage")
interface MermaidIdentifierSymbol: MermaidSymbol, SearchTarget {
  val text: String

  override fun createPointer(): Pointer<out MermaidIdentifierSymbol>
}
