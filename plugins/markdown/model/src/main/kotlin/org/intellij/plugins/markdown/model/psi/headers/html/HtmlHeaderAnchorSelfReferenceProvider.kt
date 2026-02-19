package org.intellij.plugins.markdown.model.psi.headers.html

import com.intellij.model.Symbol
import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.model.psi.PsiSymbolReferenceHints
import com.intellij.model.psi.PsiSymbolReferenceProvider
import com.intellij.model.search.SearchRequest
import com.intellij.openapi.project.Project
import com.intellij.psi.xml.XmlAttributeValue

internal class HtmlHeaderAnchorSelfReferenceProvider: PsiSymbolReferenceProvider {
  override fun getReferences(element: PsiExternalReferenceHost, hints: PsiSymbolReferenceHints): Collection<PsiSymbolReference> {
    if (element !is XmlAttributeValue) {
      return emptyList()
    }
    if (!element.isValidAnchorAttributeValue()) {
      return emptyList()
    }
    val value = element.value
    if (value.isEmpty()) {
      return emptyList()
    }
    val reference = HtmlHeaderAnchorSelfReference(element)
    return listOf(reference)
  }

  override fun getSearchRequests(project: Project, target: Symbol): Collection<SearchRequest> {
    return emptyList()
  }
}
