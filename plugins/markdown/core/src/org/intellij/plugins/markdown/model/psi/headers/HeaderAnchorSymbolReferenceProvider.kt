package org.intellij.plugins.markdown.model.psi.headers

import com.intellij.model.Symbol
import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.model.psi.PsiSymbolReferenceHints
import com.intellij.model.psi.PsiSymbolReferenceProvider
import com.intellij.model.search.SearchRequest
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLinkDestination

internal class HeaderAnchorSymbolReferenceProvider: PsiSymbolReferenceProvider {
  override fun getReferences(element: PsiExternalReferenceHost, hints: PsiSymbolReferenceHints): Collection<PsiSymbolReference> {
    if (element !is MarkdownLinkDestination) {
      return emptyList()
    }
    val range = ElementManipulators.getValueTextRange(element)
    val elementText = element.getText()
    val anchorTextRange = calculateAnchorTextRange(elementText, range) ?: return emptyList()
    val anchor = anchorTextRange.substring(elementText).lowercase()
    val reference = HeaderAnchorLinkDestinationReference(
      element,
      anchorRangeInElement = anchorTextRange,
      anchorText = anchor,
      // If there is no prefix before '#', there is no other file reference
      searchInCurrentFileOnly = anchorTextRange.startOffset == range.startOffset
    )
    return listOf(reference)
  }

  override fun getSearchRequests(project: Project, target: Symbol): Collection<SearchRequest> {
    return emptyList()
  }

  companion object {
    private fun calculateAnchorTextRange(elementText: String, valueTextRange: TextRange): TextRange? {
      val anchorOffset = elementText.indexOf('#')
      if (anchorOffset == -1) {
        return null
      }
      val endOffset = valueTextRange.endOffset
      val endIndex = when {
        endOffset <= anchorOffset -> anchorOffset + 1
        else -> endOffset
      }
      return TextRange(anchorOffset + 1, endIndex)
    }
  }
}
