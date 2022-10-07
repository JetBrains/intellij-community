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
