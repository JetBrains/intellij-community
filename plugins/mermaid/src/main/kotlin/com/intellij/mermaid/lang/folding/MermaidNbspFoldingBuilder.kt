package com.intellij.mermaid.lang.folding

import ai.grazie.nlp.utils.length
import com.intellij.lang.ASTNode
import com.intellij.lang.folding.CustomFoldingBuilder
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.mermaid.lang.intention.NbspAnnotator.Companion.nbspRegex
import com.intellij.mermaid.lang.psi.MermaidFile
import com.intellij.mermaid.lang.psi.MermaidNamedPsiElement
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.SyntaxTraverser
import com.intellij.refactoring.suggested.startOffset

class MermaidNbspFoldingBuilder : CustomFoldingBuilder() {
  override fun buildLanguageFoldRegions(
    descriptors: MutableList<FoldingDescriptor>,
    root: PsiElement,
    document: Document,
    quick: Boolean
  ) {
    if (root !is MermaidFile) {
      return
    }

    val elements = SyntaxTraverser.psiTraverser(root).filterIsInstance<MermaidNamedPsiElement>()
    for (element in elements) {
      val matches = nbspRegex.findAll(element.text)
      val rangeSequence = matches.map {
        val startOffset = element.startOffset + it.range.first
        val endOffset = startOffset + it.range.length
        TextRange.create(startOffset, endOffset)
      }
      val spaceRangesList = rangeSequence
        .fold(mutableListOf(mutableListOf<TextRange>())) { ranges, r ->
          val lastRangeList = ranges.lastOrNull()

          if (lastRangeList != null) {
            val lastRange = lastRangeList.lastOrNull()
            if (lastRange == null || lastRange.endOffset == r.startOffset) {
              lastRangeList.add(r)
            } else {
              ranges.add(mutableListOf(r))
            }
          }
          return@fold ranges
        }
        .filter { it.isNotEmpty() }
      for (spaceRange in spaceRangesList) {
        val range = TextRange.create(spaceRange.first().startOffset, spaceRange.last().endOffset)
        val placeholderText = " ".repeat(spaceRange.size)
        descriptors.add(FoldingDescriptor(element.node, range, null, placeholderText, true, emptySet()))
      }
    }
  }

  override fun getLanguagePlaceholderText(node: ASTNode, range: TextRange): String {
    return getPlaceholderText(node, range) ?: ""
  }

  override fun isRegionCollapsedByDefault(node: ASTNode): Boolean {
    return true
  }
}
