// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.documentation.doctest

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.TextRange
import com.jetbrains.python.psi.PyIndentUtil
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.CompositeASTNode
import org.intellij.markdown.ast.accept
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.ast.visitors.Visitor
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser

/**
 * Finds Markdown fenced code blocks (` ``` ` / `~~~`)
 */
internal object PyDocstringMarkdownFences {
  private val FLAVOUR = CommonMarkFlavourDescriptor()

  /**
   * @param content docstring text with the surrounding prefix and quotes already stripped
   * @param pythonLanguages accepted fence info-strings, lower-case (e.g. `py`, `python`)
   * @return the content ranges of fenced blocks whose language is in [pythonLanguages], relative to [content]
   */
  @JvmStatic
  fun pythonFenceContentRanges(content: CharSequence, pythonLanguages: Set<String>): List<TextRange> {
    if (content.isEmpty()) return emptyList()

    val lines = content.split('\n')
    val dedentedLines = PyIndentUtil.removeCommonIndent(lines, true)
    val dedented = dedentedLines.joinToString("\n")
    val originalLineStart = lineStartOffsets(lines)
    val dedentedLineStart = lineStartOffsets(dedentedLines)

    val ranges = ArrayList<TextRange>()
    MarkdownParser(FLAVOUR).buildMarkdownTreeFromString(dedented).accept(object : Visitor {
      override fun visitNode(node: ASTNode) {
        when {
          node.type == MarkdownElementTypes.CODE_FENCE ->
            node.collectFence(dedented, dedentedLineStart, lines, originalLineStart, pythonLanguages, ranges)
          node is CompositeASTNode -> {
            ProgressManager.checkCanceled()
            node.children.forEach { visitNode(it) }
          }
        }
      }
    })
    return ranges
  }

  private fun ASTNode.collectFence(
    dedented: String,
    dedentedLineStart: IntArray,
    lines: List<String>,
    originalLineStart: IntArray,
    pythonLanguages: Set<String>,
    out: MutableList<TextRange>,
  ) {
    val language = children.firstOrNull { it.type == MarkdownTokenTypes.FENCE_LANG }
      ?.getTextInNode(dedented)?.trim()?.takeWhile { !it.isWhitespace() }?.toString()?.lowercase()
    if (language == null || language !in pythonLanguages) return

    val contentLines = children.filter { it.type == MarkdownTokenTypes.CODE_FENCE_CONTENT }
    if (contentLines.isEmpty()) return

    // Content nodes are line-aligned, so map them back to whole original lines (keeping their indentation).
    val firstLine = lineAt(dedentedLineStart, contentLines.first().startOffset)
    val lastLine = lineAt(dedentedLineStart, contentLines.last().startOffset)
    val start = originalLineStart[firstLine]
    val end = originalLineStart[lastLine] + lines[lastLine].length
    if (start < end) out.add(TextRange.create(start, end))
  }

  private fun lineStartOffsets(lines: List<String>): IntArray {
    val starts = IntArray(lines.size)
    var offset = 0
    lines.forEachIndexed { index, line ->
      starts[index] = offset
      offset += line.length + 1 // + the '\n' that split() removed
    }
    return starts
  }

  /** Index of the line whose dedented start offset is the greatest one not exceeding [offset]. */
  private fun lineAt(dedentedLineStart: IntArray, offset: Int): Int =
    dedentedLineStart.indexOfLast { it <= offset }
}
