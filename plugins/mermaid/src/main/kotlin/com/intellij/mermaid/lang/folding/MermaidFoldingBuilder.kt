package com.intellij.mermaid.lang.folding

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.CustomFoldingBuilder
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.mermaid.lang.lexer.MermaidTokens
import com.intellij.mermaid.lang.psi.*
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import com.intellij.psi.util.siblings

class MermaidFoldingBuilder : CustomFoldingBuilder() {
  override fun buildLanguageFoldRegions(
    descriptors: MutableList<FoldingDescriptor>,
    root: PsiElement,
    document: Document,
    quick: Boolean
  ) {
    if (root !is MermaidFile) {
      return
    }
    root.accept(object : MermaidVisitor() {
      override fun visitElement(element: PsiElement) {
        super.visitElement(element)
        element.acceptChildren(this)
      }

      override fun visitFoldableElement(o: MermaidFoldableElement) {
        addDescriptors(o, o.textRange)
        super.visitFoldableElement(o)
      }

      override fun visitDiagramDocument(o: MermaidDiagramDocument) {
        val startOffset = o.textRange.startOffset
        val endOffset = trimWhiteSpacesForward(o).textRange.endOffset
        addDescriptors(o, TextRange(startOffset, endOffset))
        super.visitDiagramDocument(o)
      }

      override fun visitDiagramInBracesDocument(o: MermaidDiagramInBracesDocument) {
        val startOffset = trimWhiteSpacesBackward(o).textRange.startOffset
        val endOffset = if (o.nextSibling.elementType == MermaidTokens.WHITE_SPACE) {
          o.nextSibling
        } else {
          o
        }.textRange.endOffset
        addDescriptors(o, TextRange(startOffset, endOffset))
        super.visitDiagramInBracesDocument(o)
      }

      private fun addDescriptors(element: PsiElement, range: TextRange) {
        addDescriptors(element, range, descriptors, document)
      }
    })
  }

  private fun addDescriptors(
    element: PsiElement,
    range: TextRange,
    descriptors: MutableList<in FoldingDescriptor>,
    document: Document
  ) {
    if (document.getLineNumber(range.startOffset) != document.getLineNumber(range.endOffset - 1)) {
      descriptors.add(FoldingDescriptor(element, range))
    }
  }

  private fun trimWhiteSpacesForward(element: PsiElement): PsiElement {
    if (element.lastChild.elementType == MermaidTokens.EOL) {
      return element.lastChild.prevSibling ?: element
    }
    return element
  }

  private fun trimWhiteSpacesBackward(element: PsiElement): PsiElement {
    return element.siblings(forward = false, withSelf = false).firstOrNull { it.elementType == MermaidTokens.EOL }
      ?: element
  }

  override fun getLanguagePlaceholderText(node: ASTNode, range: TextRange): String {
    return StringUtil.shortenTextWithEllipsis(node.text, 30, 5)
  }

  override fun isRegionCollapsedByDefault(node: ASTNode): Boolean {
    return false
  }
}
