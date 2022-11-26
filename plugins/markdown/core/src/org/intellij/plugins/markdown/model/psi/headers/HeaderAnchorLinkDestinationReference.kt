package org.intellij.plugins.markdown.model.psi.headers

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.model.Symbol
import com.intellij.model.psi.PsiCompletableReference
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import org.intellij.plugins.markdown.MarkdownIcons
import org.intellij.plugins.markdown.lang.index.HeaderAnchorIndex
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeader
import org.intellij.plugins.markdown.lang.psi.util.childrenOfType
import org.intellij.plugins.markdown.model.psi.MarkdownPsiSymbolReferenceBase
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class HeaderAnchorLinkDestinationReference(
  element: PsiElement,
  anchorRangeInElement: TextRange,
  private val anchorText: String,
  private val file: PsiFile
): MarkdownPsiSymbolReferenceBase(element, anchorRangeInElement), PsiCompletableReference {
  override fun resolveReference(): Collection<Symbol> {
    val headers = HeaderAnchorIndex.collectHeaders(element.project, GlobalSearchScope.fileScope(file), anchorText)
    val symbols = headers.mapNotNull { HeaderSymbol.createPointer(it)?.dereference() }
    return symbols.toList()
  }

  override fun getCompletionVariants(): Collection<LookupElement> {
    val headers = file.childrenOfType<MarkdownHeader>().orEmpty()
    val anchors = headers.mapNotNull { header -> header.anchorText?.let { it to header.level } }
    return anchors.mapTo(ArrayList()) { (anchorText, level) ->
      LookupElementBuilder.create(anchorText)
        // TODO: Replace with actual header icon
        .withIcon(MarkdownIcons.EditorActions.Link)
        .withTypeText("H$level", true)
    }
  }
}
