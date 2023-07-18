package org.intellij.plugins.markdown.model.psi.headers

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.model.Symbol
import com.intellij.model.psi.PsiCompletableReference
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SyntaxTraverser
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.childrenOfType
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.util.PlatformIcons
import org.intellij.plugins.markdown.MarkdownIcons
import org.intellij.plugins.markdown.lang.index.HeaderAnchorIndex
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeader
import org.intellij.plugins.markdown.model.psi.MarkdownPsiSymbolReferenceBase
import org.intellij.plugins.markdown.model.psi.headers.html.HtmlAnchorSymbol
import org.intellij.plugins.markdown.model.psi.headers.html.findInjectedHtmlFile
import org.intellij.plugins.markdown.model.psi.headers.html.isValidAnchorAttributeValue
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
    val headerSymbols = headers.mapNotNull { HeaderSymbol.createPointer(it)?.dereference() }
    val injectedSymbols = collectInjectedHtmlAnchors()
    return headerSymbols + injectedSymbols
  }

  private fun collectInjectedHtmlAnchors(): Sequence<HtmlAnchorSymbol> {
    val injectedFile = findInjectedHtmlFile(file) ?: return emptySequence()
    val htmlValues = collectHtmlAnchorAttributesValues(injectedFile).filter { it.value == anchorText }
    return htmlValues.map { HtmlAnchorSymbol(file, it.valueTextRange, anchorText) }
  }

  override fun getCompletionVariants(): Collection<LookupElement> {
    val headers = file.childrenOfType<MarkdownHeader>()
    val anchors = headers.mapNotNull { header -> header.anchorText?.let { it to header.level } }
    val result = ArrayList<LookupElement>()
    for ((anchorText, level) in anchors) {
      val lookup = LookupElementBuilder.create(anchorText)
        // TODO: Replace with actual header icon
        .withIcon(MarkdownIcons.EditorActions.Link)
        .withTypeText("H$level", true)
      result.add(lookup)
    }
    val injectedFile = findInjectedHtmlFile(file)
    if (injectedFile != null) {
      val values = collectHtmlAnchorAttributesValues(injectedFile)
      for (value in values) {
        val lookup = LookupElementBuilder.create(value.value)
          .withIcon(PlatformIcons.XML_TAG_ICON)
          .withTypeText("Anchor", true)
        result.add(lookup)
      }
    }
    return result
  }

  private fun collectHtmlAnchorAttributesValues(file: PsiFile): Sequence<XmlAttributeValue> {
    val traverser = SyntaxTraverser.psiTraverser(file).asSequence()
    val values = traverser.filterIsInstance<XmlAttributeValue>()
    return values.filter { it.isValidAnchorAttributeValue() }
  }
}
