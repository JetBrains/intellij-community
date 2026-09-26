// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.lang.folding

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

    val elements = SyntaxTraverser.psiTraverser(root).asSequence().filterIsInstance<MermaidNamedPsiElement>()
    for (element in elements) {
      val matches = nbspRegex.findAll(element.text)
      val rangeSequence = matches.map {
        val startOffset = element.startOffset + it.range.first
        val endOffset = startOffset + (it.range.last - it.range.first + 1)
        TextRange.create(startOffset, endOffset)
      }
      if (rangeSequence.any()) {
        var spaceRange = rangeSequence.first()
        var length = 1

        for (range in rangeSequence) {
          if (spaceRange.endOffset == range.startOffset) {
            spaceRange = TextRange(spaceRange.startOffset, range.endOffset)
            length++
          } else {
            addDescriptor(descriptors, element, spaceRange, length)

            spaceRange = range
            length = 1
          }
        }

        addDescriptor(descriptors, element, spaceRange, length)
      }
    }
  }

  private fun addDescriptor(descriptors: MutableList<FoldingDescriptor>, element: PsiElement, spaceRange: TextRange, length: Int) {
    val placeholderText = " ".repeat(length)
    descriptors.add(FoldingDescriptor(element.node, spaceRange, null, placeholderText, true, emptySet()))
  }

  override fun getLanguagePlaceholderText(node: ASTNode, range: TextRange): String {
    return getPlaceholderText(node, range) ?: ""
  }

  override fun isRegionCollapsedByDefault(node: ASTNode): Boolean {
    return true
  }
}
