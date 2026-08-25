package org.intellij.plugins.markdown.model.psi.labels

import com.intellij.model.Symbol
import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.model.psi.PsiSymbolReferenceHints
import com.intellij.model.psi.PsiSymbolReferenceProvider
import com.intellij.model.search.SearchRequest
import com.intellij.openapi.project.Project
import com.intellij.psi.util.parentOfType
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownImage
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLinkLabel
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLinkText
import org.intellij.plugins.markdown.model.psi.labels.LinkLabelSymbol.Companion.isDeclaration
import org.intellij.plugins.markdown.util.isFootnoteLabelText

internal class LinkLabelSymbolReferenceProvider: PsiSymbolReferenceProvider {
  override fun getReferences(element: PsiExternalReferenceHost, hints: PsiSymbolReferenceHints): Collection<PsiSymbolReference> {
    if (element !is MarkdownLinkLabel || element.isDeclaration || element.isImageLabel) {
      return emptyList()
    }
    if (element.parentOfType<MarkdownLinkText>() != null) {
      return emptyList()
    }
    val elementText = element.text
    val rangeInElement = element.labelTextRange
    val text = rangeInElement.substring(elementText)
    // Footnote reference labels are handled separately and are not link-label symbols.
    if (isFootnoteLabelText(elementText)) {
      return emptyList()
    }
    return listOf(LinkLabelSymbolReference(element, rangeInElement, text))
  }

  private val MarkdownLinkLabel.isImageLabel: Boolean
    get() {
      val image = parentOfType<MarkdownImage>() ?: return false
      val altLabelStart = image.text.indexOf('[')
      return altLabelStart >= 0 && textRange.startOffset == image.textRange.startOffset + altLabelStart
    }

  override fun getSearchRequests(project: Project, target: Symbol): Collection<SearchRequest> {
    return emptyList()
  }
}
