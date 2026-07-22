package org.intellij.plugins.markdown.model.psi.labels

import com.intellij.model.Symbol
import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.model.psi.PsiSymbolReferenceHints
import com.intellij.model.psi.PsiSymbolReferenceProvider
import com.intellij.model.search.SearchRequest
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLinkLabel
import org.intellij.plugins.markdown.model.psi.labels.LinkLabelSymbol.Companion.isDeclaration
import org.intellij.plugins.markdown.model.psi.labels.LinkLabelSymbol.Companion.isShortLink
import org.intellij.plugins.markdown.util.isFootnoteLabelText

internal class LinkLabelSymbolReferenceProvider: PsiSymbolReferenceProvider {
  override fun getReferences(element: PsiExternalReferenceHost, hints: PsiSymbolReferenceHints): Collection<PsiSymbolReference> {
    if (element !is MarkdownLinkLabel || element.isDeclaration) {
      return emptyList()
    }
    if (element.isShortLink && !Registry.`is`("markdown.validate.short.links")) {
      return emptyList()
    }
    val elementText = element.text
    val rangeInElement = element.labelTextRange
    val text = rangeInElement.substring(elementText)
    // Footnote reference labels [^...] inside a full reference link [^a][^b] are not link definitions
    if (isFootnoteLabelText(elementText) && element.parent?.node?.elementType == MarkdownElementTypes.FULL_REFERENCE_LINK) {
      return emptyList()
    }
    val reference = LinkLabelSymbolReference(element, rangeInElement, text)
    return listOf(reference)
  }

  override fun getSearchRequests(project: Project, target: Symbol): Collection<SearchRequest> {
    return emptyList()
  }
}
