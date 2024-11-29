// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.python.grazie

import com.intellij.grazie.text.TextContent
import com.intellij.grazie.text.TextContent.TextDomain
import com.intellij.grazie.text.TextContentBuilder
import com.intellij.grazie.text.TextExtractor
import com.intellij.grazie.utils.Text
import com.intellij.grazie.utils.getNotSoDistantSimilarSiblings
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.impl.source.tree.PsiCommentImpl
import com.intellij.psi.util.PsiUtilCore
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.PyTokenTypes.FSTRING_TEXT
import com.jetbrains.python.documentation.docstrings.SphinxDocString
import com.jetbrains.python.psi.PyFormattedStringElement
import java.util.regex.Pattern

private val KNOWN_DOCSTRING_TAGS_PATTERN = SphinxDocString.ALL_TAGS.joinToString("|", transform = Pattern::quote, prefix = "(", postfix = ")")
private val DOCSTRING_DIRECTIVE_PATTERN = "^$KNOWN_DOCSTRING_TAGS_PATTERN[^\n:]*: *".toPattern(Pattern.MULTILINE)

internal class PythonTextExtractor : TextExtractor() {
  override fun buildTextContent(root: PsiElement, allowedDomains: MutableSet<TextDomain>): TextContent? {
    val elementType = PsiUtilCore.getElementType(root)
    if (elementType in PyTokenTypes.STRING_NODES) {
      val domain = if (elementType == PyTokenTypes.DOCSTRING) TextDomain.DOCUMENTATION else TextDomain.LITERALS
      val stringContent = TextContentBuilder.FromPsi.removingIndents(" \t")
        .removingLineSuffixes(" \t")
        .withUnknown(this::isUnknownFragment)
        .build(root.parent, domain)
      if (stringContent != null && domain == TextDomain.DOCUMENTATION && TextDomain.DOCUMENTATION in allowedDomains) {
        return stringContent.excludeDocstringTags()
      }
      return stringContent
    }

    if (root is PsiCommentImpl) {
      val siblings = getNotSoDistantSimilarSiblings(root) { it is PsiCommentImpl }
      return TextContent.joinWithWhitespace('\n', siblings.mapNotNull { TextContent.builder().build(it, TextDomain.COMMENTS) })
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

private fun TextContent.excludeDocstringTags(): TextContent? {
  return excludeRanges(Text.allOccurrences(DOCSTRING_DIRECTIVE_PATTERN, this).map(TextContent.Exclusion::exclude))
}