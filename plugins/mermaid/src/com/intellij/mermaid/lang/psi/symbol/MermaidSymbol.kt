// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.lang.psi.symbol

import com.intellij.model.Symbol
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile

@Suppress("UnstableApiUsage")
interface MermaidSymbol : Symbol {
  val file: PsiFile
  val range: TextRange
  val searchText: String
}
