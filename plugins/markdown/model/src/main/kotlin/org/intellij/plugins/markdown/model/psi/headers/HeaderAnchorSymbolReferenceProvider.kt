package org.intellij.plugins.markdown.model.psi.headers

import com.intellij.model.Symbol
import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.model.psi.PsiSymbolReferenceHints
import com.intellij.model.psi.PsiSymbolReferenceProvider
import com.intellij.model.search.SearchRequest
import com.intellij.openapi.project.Project
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReferenceService
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceUtil
import org.intellij.plugins.markdown.lang.isMarkdownType
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLinkDefinition
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLinkDestination
import org.intellij.plugins.markdown.lang.references.paths.FileWithoutExtensionReference
import org.intellij.plugins.markdown.util.MarkdownLinkFragmentUtil

internal class HeaderAnchorSymbolReferenceProvider: PsiSymbolReferenceProvider {
  override fun getReferences(element: PsiExternalReferenceHost, hints: PsiSymbolReferenceHints): Collection<PsiSymbolReference> {
    if (element !is MarkdownLinkDestination) {
      return emptyList()
    }
    if (MarkdownLinkDefinition.isUnderCommentWrapper(element)) {
      return emptyList()
    }
    val range = ElementManipulators.getValueTextRange(element)
    val elementText = element.text
    val anchorTextRange = MarkdownLinkFragmentUtil.getFragmentRange(elementText, range) ?: return emptyList()
    val anchor = anchorTextRange.substring(elementText).lowercase()
    val hasPrefix = anchorTextRange.startOffset != range.startOffset
    val referencedFile = findExistingFileFromReferences(element)
    if (hasPrefix && referencedFile == null) {
      // If there is a prefix before '#' and there are no potential file references there,
      // consider this a non header link (there might be a plain http link like 'https://jetbrains.com#some')
      return emptyList()
    }
    if (referencedFile != null && !referencedFile.fileType.isMarkdownType()) {
      return emptyList()
    }
    val reference = HeaderAnchorLinkDestinationReference(
      element,
      anchorRangeInElement = anchorTextRange,
      anchorText = anchor,
      file = referencedFile ?: element.containingFile
    )
    return listOf(reference)
  }

  override fun getSearchRequests(project: Project, target: Symbol): Collection<SearchRequest> {
    return emptyList()
  }

  companion object {
    private fun findExistingFileFromReferences(element: PsiElement): PsiFile? {
      val elementReferences = PsiReferenceService.getService().getReferences(element, PsiReferenceService.Hints.NO_HINTS)
      val file = FileReferenceUtil.findFile(*elementReferences.toTypedArray())
      if (file != null) {
        return file
      }
      val references = elementReferences.asSequence()
      val fileWithoutExtensionReference = references.filterIsInstance<FileWithoutExtensionReference>().firstOrNull()
      return fileWithoutExtensionReference?.let { it.resolve() as? PsiFile }
    }
  }
}
