// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.lang.formatter.blocks

import com.intellij.formatting.Alignment
import com.intellij.formatting.SpacingBuilder
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.codeStyle.CodeStyleSettings

/**
 * Markdown formatting block that trims its text-range
 * to non-whitespace text. Can be used to trim whitespaces
 * that are forcefully added to AST elements by parser
 */
internal class MarkdownTrimmedFormattingBlock(
  settings: CodeStyleSettings, spacing: SpacingBuilder,
  node: ASTNode, alignment: Alignment? = null
) : MarkdownFormattingBlock(settings, spacing, node, alignment) {
  private val range by lazy {
    val text = node.text
    val dropStart = text.takeWhile { it.isWhitespace() }.count()
    val dropLast = text.reversed().takeWhile { it.isWhitespace() }.count()
    TextRange.from(node.textRange.startOffset + dropStart, node.textRange.length - dropLast - dropStart)
  }

  override fun getTextRange() = range
}