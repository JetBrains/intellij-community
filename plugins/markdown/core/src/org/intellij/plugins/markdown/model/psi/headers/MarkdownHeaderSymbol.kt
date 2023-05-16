package org.intellij.plugins.markdown.model.psi.headers

import com.intellij.openapi.util.NlsSafe
import org.intellij.plugins.markdown.model.psi.MarkdownSymbolWithUsages
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface MarkdownHeaderSymbol: MarkdownSymbolWithUsages {
  val text: @NlsSafe String
  val anchorText: @NlsSafe String
}
