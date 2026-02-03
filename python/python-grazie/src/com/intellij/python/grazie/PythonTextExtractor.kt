// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.python.grazie

import com.intellij.grazie.text.TextContent
import com.intellij.grazie.text.TextContent.TextDomain
import com.intellij.grazie.text.TextContentBuilder
import com.intellij.grazie.text.TextExtractor
import com.intellij.grazie.utils.Text
import com.intellij.grazie.utils.getNotSoDistantSimilarSiblings
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.PsiCommentImpl
import com.intellij.psi.util.PsiUtilCore
import com.jetbrains.python.PyStringFormatParser
import com.jetbrains.python.PyStringFormatParser.ConstantChunk
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.documentation.docstrings.SphinxDocString
import com.jetbrains.python.psi.PyBinaryExpression
import com.jetbrains.python.psi.PyFormattedStringElement
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jetbrains.python.psi.impl.PyStringLiteralDecoder
import java.util.regex.Pattern

private val KNOWN_DOCSTRING_TAGS_PATTERN = SphinxDocString.ALL_TAGS.joinToString("|", transform = Pattern::quote, prefix = "(", postfix = ")")
private val DOCSTRING_DIRECTIVE_PATTERN = "^$KNOWN_DOCSTRING_TAGS_PATTERN[^\n:]*: *".toPattern(Pattern.MULTILINE)

internal class PythonTextExtractor : TextExtractor() {
  override fun buildTextContents(root: PsiElement, allowedDomains: Set<TextDomain>): List<TextContent> {
    if (root is PyStringLiteralExpression) {
      val texts = mutableListOf<TextContent>()
      val parent = root.parent
      if (parent is PyBinaryExpression && root === parent.leftExpression && parent.operator === PyTokenTypes.PERC) {
        PyStringFormatParser.parsePercentFormat(root.stringValue).forEach { chunk ->
          if (chunk is ConstantChunk) {
            val startIndex = root.valueOffsetToTextOffset(chunk.startIndex)
            val endIndex = root.valueOffsetToTextOffset(chunk.endIndex)
            TextContentBuilder.FromPsi.build(root, TextDomain.LITERALS, TextRange(startIndex, endIndex))?.let { texts.add(it) }
          }
        }
        return texts
      }

      root.stringElements.forEach { element ->
        val ranges = if (element.isFormatted) (element as PyFormattedStringElement).literalPartRanges else listOf(element.contentRange)
        val decoder = PyStringLiteralDecoder(element)
        val containsEscapes = element.textContains('\\')
        ranges.forEach { range ->
          val escapeAwareRanges = if (element.isRaw || !containsEscapes) listOf(range) else decoder.decodeRange(range).map { it.first }
          escapeAwareRanges.forEach { escapeAwareRange ->
            val domain = getDomain(element)
            TextContentBuilder.FromPsi
              .removingIndents(" \t")
              .removingLineSuffixes(" \t")
              .build(element, domain, escapeAwareRange)?.let { text ->
                if (domain == TextDomain.DOCUMENTATION) text.excludeDocstringTags()?.let { texts.add(it) } else texts.add(text)
              }
          }
        }
      }
      return texts
    }

    if (root is PsiCommentImpl && TextDomain.COMMENTS in allowedDomains) {
      val siblings = getNotSoDistantSimilarSiblings(root) { it is PsiCommentImpl }
      val text = TextContent.joinWithWhitespace(
        '\n',
        siblings.mapNotNull { TextContent.builder().build(it, TextDomain.COMMENTS) }
      ) ?: return emptyList()
      return listOf(text)
    }

    return emptyList()
  }

  private fun getDomain(element: PsiElement): TextDomain {
    val elementType = PsiUtilCore.getElementType(element)
    return if (elementType == PyTokenTypes.DOCSTRING) TextDomain.DOCUMENTATION else TextDomain.LITERALS
  }
}

private fun TextContent.excludeDocstringTags(): TextContent? {
  return excludeRanges(Text.allOccurrences(DOCSTRING_DIRECTIVE_PATTERN, this).map(TextContent.Exclusion::exclude))
}