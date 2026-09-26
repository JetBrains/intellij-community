// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.lang.formatter.blocks

import com.intellij.formatting.Alignment
import com.intellij.formatting.SpacingBuilder
import com.intellij.formatting.Wrap
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.codeStyle.CodeStyleSettings

internal class TableSeparatorFormattingBlock(
  node: ASTNode,
  settings: CodeStyleSettings,
  spacing: SpacingBuilder,
  alignment: Alignment? = null,
  wrap: Wrap? = null,
) : MarkdownFormattingBlock(node, settings, spacing, alignment = alignment, wrap = wrap) {
  private val trimmedRange: TextRange = computeTrimmedRange(node)

  override fun getTextRange(): TextRange = trimmedRange

  companion object {
    private fun computeTrimmedRange(node: ASTNode): TextRange {
      if (node.textLength <= 1) return node.textRange
      val text = node.text
      val dropStart = text.takeWhile { it.isWhitespace() }.count()
      val dropLast = text.reversed().takeWhile { it.isWhitespace() }.count()
      if (dropStart + dropLast >= text.length) return node.textRange
      val start = node.textRange.startOffset + dropStart
      val end = node.textRange.endOffset - dropLast
      return TextRange(start, end)
    }
  }
}
