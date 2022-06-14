package org.intellij.plugins.markdown.model.psi.headers

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.model.Symbol
import com.intellij.model.psi.PsiCompletableReference
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceService
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceUtil
import com.intellij.psi.search.GlobalSearchScope
import org.intellij.plugins.markdown.MarkdownIcons
import org.intellij.plugins.markdown.lang.index.HeaderAnchorIndex
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeader
import org.intellij.plugins.markdown.lang.psi.util.childrenOfType
import org.intellij.plugins.markdown.lang.references.paths.FileWithoutExtensionReference
import org.intellij.plugins.markdown.model.psi.MarkdownPsiSymbolReferenceBase
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class HeaderAnchorLinkDestinationReference(
  element: PsiElement,
  anchorRangeInElement: TextRange,
  private val anchorText: String,
  private val searchInCurrentFileOnly: Boolean
): MarkdownPsiSymbolReferenceBase(element, anchorRangeInElement), PsiCompletableReference {
  private fun resolveFile(): PsiFile {
    if (!searchInCurrentFileOnly) {
      val references = PsiReferenceService.getService().getReferences(element, PsiReferenceService.Hints.NO_HINTS)
      val otherFile = findExistingFileFromReferences(references)
      if (otherFile != null) {
        return otherFile
      }
    }
    return element.containingFile
  }

  override fun resolveReference(): Collection<Symbol> {
    val file = resolveFile()
    //val headers = file.firstChild?.childrenOfType<MarkdownHeader>().orEmpty()
    //val targetHeaders = headers.filter { it.anchorText == anchorText }
    val headers = HeaderAnchorIndex.collectHeaders(element.project, GlobalSearchScope.fileScope(file), anchorText)
    val symbols = headers.mapNotNull { HeaderSymbol.createPointer(it)?.dereference() }
    return symbols.toList()
  }

  override fun getCompletionVariants(): Collection<LookupElement> {
    // Find target file and get all it's headers
    val file = resolveFile()
    val headers = file.firstChild?.childrenOfType<MarkdownHeader>().orEmpty()
    val anchors = headers.mapNotNull { header -> header.anchorText?.let { it to header.level } }
    return anchors.map { (anchorText, level) ->
      LookupElementBuilder.create(anchorText)
        // TODO: Replace with actual header icon
        .withIcon(MarkdownIcons.EditorActions.Link)
        .withTypeText("H$level", true)
    }.toList()
  }

  companion object {
    private fun findExistingFileFromReferences(alreadyCreatedReferencesForElement: MutableList<PsiReference>): PsiFile? {
      val file = FileReferenceUtil.findFile(*alreadyCreatedReferencesForElement.toTypedArray())
      if (file != null) {
        return file
      }
      val references = alreadyCreatedReferencesForElement.asSequence()
      val fileWithoutExtensionReference = references.filterIsInstance<FileWithoutExtensionReference>().firstOrNull()
      return fileWithoutExtensionReference?.let { it.resolve() as? PsiFile }
    }
  }
}
