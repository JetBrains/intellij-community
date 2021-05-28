// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.chat.ui.discussion

import circlet.code.api.CodeDiscussionAnchor
import circlet.code.api.CodeDiscussionSnippet
import com.intellij.diff.util.DiffDrawUtil
import com.intellij.diff.util.TextDiffType
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.LineNumberConverter
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.space.messages.SpaceBundle
import com.intellij.ui.JBColor
import com.intellij.ui.JBColor.namedColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.codereview.timeline.TimelineDiffComponentFactory
import org.jetbrains.annotations.Nls
import runtime.code.DiffLineType
import runtime.code.InlineDiffLine
import runtime.text.TextRange
import runtime.text.end
import java.awt.Color
import javax.swing.JComponent

internal fun createDiffComponent(
  project: Project,
  anchor: CodeDiscussionAnchor,
  snippet: CodeDiscussionSnippet.InlineDiffSnippet
): JComponent {
  val lines = snippet.lines
  val lineSeparator = "\n"
  val text = lines.joinToString(lineSeparator) { line ->
    line.text
  }
  val anchorLine = lines.findAnchorLine(anchor)
  val editorFactory = EditorFactory.getInstance()
  return TimelineDiffComponentFactory(project, editorFactory).createDiffComponent(text) { editor ->
    editor
      .addLinesHighlighting(lines, anchorLine, lineSeparator)
      .convertLinesNumber(lines)
    editor.scrollPane.border = JBUI.Borders.empty()
  }
}

private fun EditorEx.convertLinesNumber(lines: List<InlineDiffLine>): EditorEx {
  if (lines.any { it.newLineNum != null && it.oldLineNum != null }) {
    gutter.setLineNumberConverter(
      object : LineNumberConverter {
        override fun convert(editor: Editor, lineNumber: Int) = lines[lineNumber - 1].oldLineNum?.plus(1)

        override fun getMaxLineNumber(editor: Editor): Int? = lines.mapNotNull { it.oldLineNum }.maxOrNull()
      },
      object : LineNumberConverter {
        override fun convert(editor: Editor, lineNumber: Int) = lines[lineNumber - 1].newLineNum?.plus(1)

        override fun getMaxLineNumber(editor: Editor): Int? = lines.mapNotNull { it.newLineNum }.maxOrNull()
      }
    )
  }
  else {
    gutter.setLineNumberConverter(object : LineNumberConverter {
      override fun convert(editor: Editor, lineNumber: Int) = lines[lineNumber - 1].newLineNum?.plus(1)

      override fun getMaxLineNumber(editor: Editor): Int? = lines.last().newLineNum
    })
  }
  return this
}

private fun EditorEx.addLinesHighlighting(
  lines: List<InlineDiffLine>,
  anchorLine: InlineDiffLine?,
  @NlsSafe separator: String
): EditorEx {
  val prefixLengths = ArrayList<Int>()
  prefixLengths.add(0)
  for (i in lines.indices) {
    prefixLengths.add(prefixLengths[i] + lines[i].text.length + separator.length)
  }
  lines.forEachIndexed { i, line ->
    addFullLineHighlighting(line, i, anchorLine)
    val prefixLength = prefixLengths[i]
    line.inserts?.let { ranges -> addInlineHighlighting(line, prefixLength, ranges, TextDiffType.INSERTED) }
    line.deletes?.let { ranges -> addInlineHighlighting(line, prefixLength, ranges, TextDiffType.DELETED) }
  }
  return this
}

private fun EditorEx.addFullLineHighlighting(line: InlineDiffLine, lineIndex: Int, anchorLine: InlineDiffLine?) {
  if (anchorLine != null && anchorLine == line) {
    DiffDrawUtil.createHighlighter(this, lineIndex, lineIndex + 1, AnchorLine, false)
  }
  else {
    val diffType = when (line.type) {
      DiffLineType.ADDED -> TextDiffType.INSERTED
      DiffLineType.DELETED -> TextDiffType.DELETED
      DiffLineType.MODIFIED -> TextDiffType.MODIFIED
      DiffLineType.CONFLICT_OLD -> TextDiffType.CONFLICT
      DiffLineType.CONFLICT_NEW -> TextDiffType.CONFLICT
      DiffLineType.FILTERED_ADDED -> TextDiffType.INSERTED
      DiffLineType.FILTERED_DELETED -> TextDiffType.DELETED
      DiffLineType.FILTERED_MODIFIED -> TextDiffType.MODIFIED
      null -> null
    }
    val ignored = (line.deletes?.any { range -> !isFullLineRange(line, range) } ?: false) ||
                  (line.inserts?.any { range -> !isFullLineRange(line, range) } ?: false)
    if (diffType != null) {
      DiffDrawUtil.createHighlighter(this, lineIndex, lineIndex + 1, diffType, ignored)
    }
  }
}

private fun isFullLineRange(line: InlineDiffLine, range: TextRange): Boolean = line.text.length == range.length

private fun EditorEx.addInlineHighlighting(line: InlineDiffLine, prefixLength: Int, ranges: List<TextRange>, diffType: TextDiffType) {
  ranges.forEach { range ->
    if (!isFullLineRange(line, range)) {
      DiffDrawUtil.createInlineHighlighter(this, prefixLength + range.start, prefixLength + range.end, diffType)
    }
  }
}

private fun List<InlineDiffLine>.findAnchorLine(anchor: CodeDiscussionAnchor): InlineDiffLine? =
  this.find { line ->
    line.oldLineNum != null && anchor.oldLine == line.oldLineNum ||
    line.newLineNum != null && anchor.line == line.newLineNum
  }

private object AnchorLine : TextDiffType {
  @Nls
  override fun getName() = SpaceBundle.message("review.diff.type.anchor.name")

  override fun getColor(editor: Editor?): Color = namedColor(
    "Space.Review.diffAnchorBackground",
    JBColor(0xFBF1D1, 0x544B2D)
  )

  override fun getIgnoredColor(editor: Editor?) = getColor(editor)

  override fun getMarkerColor(editor: Editor?) = getColor(editor)
}
