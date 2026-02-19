package org.intellij.plugins.markdown.model.psi.labels

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.model.Symbol
import com.intellij.model.psi.PsiCompletableReference
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.intellij.plugins.markdown.lang.psi.MarkdownRecursiveElementVisitor
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLinkLabel
import org.intellij.plugins.markdown.model.psi.MarkdownPsiSymbolReferenceBase
import org.intellij.plugins.markdown.model.psi.labels.LinkLabelSymbol.Companion.isDeclaration

internal class LinkLabelSymbolReference(
  element: PsiElement,
  rangeInElement: TextRange,
  private val text: String,
): MarkdownPsiSymbolReferenceBase(element, rangeInElement), PsiCompletableReference {
  override fun resolveReference(): Collection<Symbol> {
    val file = element.containingFile
    val declarations = file.collectLinkLabels().asSequence().filter { it.isDeclaration }
    val matchingDeclarations = declarations.filter { it.text == text }
    val symbols = matchingDeclarations.mapNotNull { LinkLabelSymbol.createPointer(it)?.dereference() }
    return symbols.toList()
  }

  override fun getCompletionVariants(): Collection<LookupElement> {
    val file = element.containingFile
    val labels = file.collectLinkLabels().asSequence()
    return labels.map { LookupElementBuilder.create(it) }.toList()
  }

  companion object {
    private fun PsiFile.collectLinkLabels(): List<MarkdownLinkLabel> {
      val elements = arrayListOf<MarkdownLinkLabel>()
      val visitor = object: MarkdownRecursiveElementVisitor() {
        override fun visitElement(element: PsiElement) {
          if (element is MarkdownLinkLabel) {
            elements.add(element)
          }
          super.visitElement(element)
        }
      }
      accept(visitor)
      return elements
    }
  }
}
