package org.intellij.plugins.markdown.model.psi

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile

internal interface MarkdownSymbolWithUsages: MarkdownSymbol {
  val file: PsiFile
  val range: TextRange
  val searchText: String
}
