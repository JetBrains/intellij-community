// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.language.python

import com.intellij.grazie.text.TextContent
import com.intellij.grazie.text.TextContentBuilder
import com.intellij.grazie.text.TextExtractor
import com.intellij.grazie.utils.getNotSoDistantSimilarSiblings
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.impl.source.tree.PsiCommentImpl
import com.intellij.psi.util.PsiUtilCore
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.PyTokenTypes.FSTRING_TEXT
import com.jetbrains.python.psi.PyFormattedStringElement

internal class PythonTextExtractor : TextExtractor() {
  override fun buildTextContent(root: PsiElement, allowedDomains: MutableSet<TextContent.TextDomain>): TextContent? {
    val elementType = PsiUtilCore.getElementType(root)
    if (elementType in PyTokenTypes.STRING_NODES) {
      val domain = if (elementType == PyTokenTypes.DOCSTRING) TextContent.TextDomain.DOCUMENTATION else TextContent.TextDomain.LITERALS
      return TextContentBuilder.FromPsi.removingIndents(" \t").removingLineSuffixes(" \t").withUnknown(this::isUnknownFragment).build(root.parent, domain)
    }

    if (root is PsiCommentImpl) {
      val siblings = getNotSoDistantSimilarSiblings(root) { it is PsiCommentImpl }
      return TextContent.joinWithWhitespace('\n', siblings.mapNotNull { TextContent.builder().build(it, TextContent.TextDomain.COMMENTS) })
    }

    return null
  }

  private fun isUnknownFragment(element: PsiElement): Boolean {
    if (element.parent is PyFormattedStringElement) {
      return element !is LeafPsiElement || element.elementType != FSTRING_TEXT
    }

    return false
  }
}
