package org.intellij.plugins.markdown.model.psi

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface MarkdownSymbolWithUsages: MarkdownSymbol {
  val file: PsiFile
  val range: TextRange
  val searchText: String
}
