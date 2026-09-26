// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.elicitation

import com.intellij.lang.Language
import com.intellij.mcpserver.elicitation.ElicitationMessagePart.Code
import com.intellij.mcpserver.elicitation.ElicitationMessagePart.FontStyle.BOLD
import com.intellij.mcpserver.elicitation.ElicitationMessagePart.FontStyle.ITALIC
import com.intellij.mcpserver.elicitation.ElicitationMessagePart.Styled
import com.intellij.mcpserver.elicitation.ElicitationMessagePart.Text
import com.intellij.openapi.project.Project

/**
 * [ElicitationFormRenderer] emitting Markdown, for clients whose chat UI renders it.
 */
object MarkdownElicitationFormRenderer : ElicitationFormRenderer {
  override fun render(parts: List<ElicitationMessagePart>, project: Project?): String = renderToMarkdown(parts)
}

/**
 * Renders message [parts] into one Markdown string, for clients that render the elicitation message
 * as Markdown and don't support ANSI.
 */
fun renderToMarkdown(parts: List<ElicitationMessagePart>): String = buildString {
  for (part in parts) {
    when (part) {
      is Text -> append(part.text.withHardLineBreaks())
      is Code -> appendFencedCode(part)
      is Styled -> append(renderStyled(part))
    }
  }
}

private fun StringBuilder.appendFencedCode(part: Code) {
  if (isNotEmpty() && !endsWith('\n')) append('\n')

  val fence = "`".repeat(fenceLength(part.text))
  append(fence).append(part.language.markdownInfoString()).append('\n')
  append(part.text)
  if (!part.text.endsWith('\n')) append('\n')
  append(fence).append('\n')
}

private fun Language.markdownInfoString(): String =
  generateSequence(this) { it.baseLanguage }.last().id.lowercase()

private fun fenceLength(code: String): Int {
  val longestRun = BACKTICK_RUN.findAll(code).maxOfOrNull { it.value.length } ?: 0
  return maxOf(MIN_FENCE_LENGTH, longestRun + 1)
}

private val BACKTICK_RUN = Regex("`+")
private const val MIN_FENCE_LENGTH = 3


private fun renderStyled(part: Styled): String {
  val core = part.text.trim()
  if (core.isEmpty()) return part.text.withHardLineBreaks()

  var styled = core.withHardLineBreaks()
  if (ITALIC in part.styles) styled = "$ITALIC_MARKER$styled$ITALIC_MARKER"
  if (BOLD in part.styles) styled = "$BOLD_MARKER$styled$BOLD_MARKER"

  val leading = part.text.takeWhile { it.isWhitespace() }.withHardLineBreaks()
  val trailing = part.text.takeLastWhile { it.isWhitespace() }.withHardLineBreaks()
  return leading + styled + trailing
}

private const val BOLD_MARKER = "**"
private const val ITALIC_MARKER = "_"

/**
 * Turns every newline into a Markdown hard line break, so the rendered layout matches the plain-text
 */
private fun String.withHardLineBreaks(): String = SOFT_LINE_BREAK.replace(this, "$HARD_LINE_BREAK\n")
private val SOFT_LINE_BREAK = Regex("(?<! {2})\n")
internal const val HARD_LINE_BREAK: String = "  "

