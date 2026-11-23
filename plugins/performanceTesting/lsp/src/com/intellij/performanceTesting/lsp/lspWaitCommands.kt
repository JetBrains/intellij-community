// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.performanceTesting.lsp

import com.intellij.codeInsight.codeVision.ui.model.CodeVisionListData
import com.intellij.codeInsight.codeVision.ui.model.PlaceholderCodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.model.TextCodeVisionEntry
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.text.AsyncEditorLoader
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

/**
 * Waits until the selected editor is the one of the given file and has loaded its content.
 *
 * `%openFile` returns after the daemon has finished in the file, and by then the results of the server are in the editor too.
 * A test that measures the time from the opening of a file to a result of the server opens the file with `%openFile -dsa`,
 * which returns at once, and then runs this command. After it, `%goto` and the waits of this package work on the new editor,
 * not on the one that was selected before.
 *
 * Syntax: `%lspWaitForEditor <the end of the path of the file>`
 */
internal class LspWaitForEditorCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: String = CMD_PREFIX + "lspWaitForEditor"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val pathEnd = extractCommandArgument(PREFIX)
    val found = withTimeoutOrNull(DEFAULT_TIMEOUT) {
      while (!withContext(Dispatchers.EDT) { isSelectedAndLoaded(context, pathEnd) }) {
        delay(5.milliseconds)
      }
    }
    if (found == null) error("The editor of '$pathEnd' was not selected and loaded in $DEFAULT_TIMEOUT")
  }

  private fun isSelectedAndLoaded(context: PlaybackContext, pathEnd: String): Boolean {
    val editor = FileEditorManager.getInstance(context.project).selectedTextEditor ?: return false
    return editor.virtualFile?.path?.endsWith(pathEnd) == true && AsyncEditorLoader.isEditorLoaded(editor)
  }
}

/**
 * Waits until a problem covers the given position of the selected editor, or with `false` at the end, until no problem covers it.
 * Lines and columns start with 1, as in `%goto`. A problem is a highlighting of at least a weak warning: the LSP client
 * gives each diagnostic of a server at least that severity.
 *
 * A position names the problem that a test expects, so the wait cannot end on another problem of the file.
 * To show that the wait ends on a new answer of the server, a test checks the opposite state before its action.
 * For the wait for no problem, pick a position whose text stays: a deleted text takes its highlighting with it,
 * before the server answers.
 *
 * Syntax: `%lspWaitForDiagnostic <line> <column> [true|false]`
 */
internal class LspWaitForDiagnosticCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: String = CMD_PREFIX + "lspWaitForDiagnostic"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val args = positionArguments(PREFIX, extractCommandArgument(PREFIX))
    awaitEditorState(context.project, args.what("problem"), stateForFailure = { "It shows: ${it.problemList()}" }) { editor ->
      val offset = editor.offsetOf(args.line, args.column)
      editor.highlightInfos().any { it.isProblem() && it.covers(offset) } == args.present
    }
  }
}

/**
 * Waits until the text at the given position of the selected editor has the colors of a semantic token,
 * or with `false` at the end, until it has none. Lines and columns start with 1, as in `%goto`.
 * The command takes each highlighting that only sets text attributes for a semantic token, except for a link.
 * So it fits a file that has no other source of such highlighting, like a file of a language that the IDE
 * supports through an LSP server only.
 *
 * Syntax: `%lspWaitForSemanticToken <line> <column> [true|false]`
 */
internal class LspWaitForSemanticTokenCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: String = CMD_PREFIX + "lspWaitForSemanticToken"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val args = positionArguments(PREFIX, extractCommandArgument(PREFIX))
    val what = args.what("semantic token")
    awaitEditorState(context.project, what, stateForFailure = { "Its line has: ${it.semanticTokenList(args.line)}" }) { editor ->
      val offset = editor.offsetOf(args.line, args.column)
      editor.highlightInfos().any { it.isSemanticToken() && it.covers(offset) } == args.present
    }
  }
}

/**
 * Waits until the name at the caret is highlighted as the element under the caret, with at least the given number
 * of occurrences highlighted in the file. The number shows that the result is the answer of the server for the name.
 *
 * The highlighting of the name at the caret before stays until the new one comes. So a test moves the caret
 * to another name: when the highlighting of the name before covers the caret, the wait ends at once.
 *
 * Syntax: `%lspWaitForDocumentHighlight <the least number of highlighted occurrences>`
 */
internal class LspWaitForDocumentHighlightCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: String = CMD_PREFIX + "lspWaitForDocumentHighlight"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val leastCount = extractCommandArgument(PREFIX).trim().toIntOrNull()
    requireNotNull(leastCount) { "Expected: $PREFIX <the least number of highlighted occurrences>, got: '$text'" }
    val what = "the highlighting of the name at the caret with $leastCount occurrences or more"
    awaitEditorState(context.project, what, stateForFailure = { "It shows: ${it.documentHighlightList()}" }) { editor ->
      val highlights = editor.documentHighlights()
      highlights.size >= leastCount && highlights.any { editor.isCaretAt(it.startOffset, it.endOffset) }
    }
  }
}

/**
 * Waits until the selected editor has an inline inlay at the given position, which shows before the character
 * of that column, or with `false` at the end, until it has none there. Lines and columns start with 1, as in `%goto`.
 *
 * Syntax: `%lspWaitForInlayHint <line> <column> [true|false]`
 */
internal class LspWaitForInlayHintCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: String = CMD_PREFIX + "lspWaitForInlayHint"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val args = positionArguments(PREFIX, extractCommandArgument(PREFIX))
    val what = args.what("inlay hint")
    awaitEditorState(context.project, what, stateForFailure = { "Its line has inlays at: ${it.inlayList(args.line)}" }) { editor ->
      val offset = editor.offsetOf(args.line, args.column)
      editor.inlayModel.getInlineElementsInRange(offset, offset).isNotEmpty() == args.present
    }
  }
}

/**
 * Waits until a code lens with the given title shows in the given line of the selected editor, or with `false`,
 * until none with that title shows there. Lines start with 1, as in `%goto`. The client shows a code lens
 * as a Code Vision entry, so the command takes the text entries of Code Vision, except for its placeholders.
 * The title names the expected answer: the server can send a lens for the line before it knows the whole project.
 *
 * Syntax: `%lspWaitForCodeLens <line> <true|false> <the title of the lens>`
 */
internal class LspWaitForCodeLensCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: String = CMD_PREFIX + "lspWaitForCodeLens"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val args = extractCommandArgument(PREFIX).trim().split(Regex("\\s+"), limit = 3)
    require(args.size == 3) { "Expected: $PREFIX <line> <true|false> <the title of the lens>, got: '$text'" }
    val line = args[0].toInt()
    val present = args[1].toBooleanStrict()
    val title = args[2]
    val what = if (present) "a code lens '$title' in line $line" else "rid of the code lens '$title' in line $line"
    awaitEditorState(context.project, what, stateForFailure = { "It shows: ${it.codeLenses()}" }) { editor ->
      editor.requireLine(line)
      editor.codeLenses().any { it.line == line && it.title == title } == present
    }
  }
}

/**
 * Waits until a fold region starts in the given line of the selected editor, or with `false` at the end,
 * until none starts there. Lines start with 1, as in `%goto`. The command takes no column: a folding range
 * of LSP names its lines, and without its optional start character, the fold region starts at the end of the line.
 *
 * Syntax: `%lspWaitForFoldRegion <line> [true|false]`
 */
internal class LspWaitForFoldRegionCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: String = CMD_PREFIX + "lspWaitForFoldRegion"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val args = extractCommandArgument(PREFIX).trim().split(Regex("\\s+"))
    require(args.size in 1..2) { "Expected: $PREFIX <line> [true|false], got: '$text'" }
    val line = args[0].toInt()
    val present = args.getOrNull(1)?.toBooleanStrict() ?: true
    val what = if (present) "a fold region that starts in line $line" else "rid of the fold region that starts in line $line"
    awaitEditorState(context.project, what, stateForFailure = { "Its fold regions start at: ${it.foldRegionList()}" }) { editor ->
      val lineRange = editor.lineRange(line)
      editor.foldingModel.allFoldRegions.any { it.isValid && it.startOffset in lineRange } == present
    }
  }
}

/** The arguments `<line> <column> [true|false]` of a wait for something at a position. */
private data class PositionArguments(val line: Int, val column: Int, val present: Boolean) {
  /** For the message of a failure: "a problem at 3:5", or with [present] `false`, "rid of the problem at 3:5". */
  fun what(noun: String): String {
    val article = if (noun.first() in "aeiou") "an" else "a"
    return if (present) "$article $noun at $line:$column" else "rid of the $noun at $line:$column"
  }
}

private fun positionArguments(prefix: String, arguments: String): PositionArguments {
  val args = arguments.trim().split(Regex("\\s+"))
  require(args.size in 2..3) { "Expected: $prefix <line> <column> [true|false], got: '$prefix $arguments'" }
  return PositionArguments(args[0].toInt(), args[1].toInt(), args.getOrNull(2)?.toBooleanStrict() ?: true)
}

private fun HighlightInfo.isProblem(): Boolean =
  severity >= HighlightSeverity.WEAK_WARNING && severity != HighlightInfoType.ELEMENT_UNDER_CARET_SEVERITY

/** `true` also for an empty range at [offset]: the LSP client can get a diagnostic with an empty range. */
private fun HighlightInfo.covers(offset: Int): Boolean = offset == startOffset || offset in startOffset until endOffset

private fun Editor.offsetOf(line: Int, column: Int): Int {
  val lineRange = lineRange(line)
  val lineLength = lineRange.last - lineRange.first
  require(column in 1..lineLength + 1) { "Column $column is out of line $line, which has $lineLength characters" }
  return lineRange.first + column - 1
}

private fun Editor.positionOf(offset: Int): String {
  val line = document.getLineNumber(offset)
  return "${line + 1}:${offset - document.getLineStartOffset(line) + 1}"
}

private fun HighlightInfo.isSemanticToken(): Boolean =
  severity == HighlightSeverity.TEXT_ATTRIBUTES &&
  forcedTextAttributesKey != null &&
  forcedTextAttributesKey != CodeInsightColors.INACTIVE_HYPERLINK_ATTRIBUTES

private fun Editor.problemList(): List<String> =
  highlightInfos().filter { it.isProblem() }.map { "${positionOf(it.startOffset)}-${positionOf(it.endOffset)} ${it.description}" }.toList()

private fun Editor.semanticTokenList(line: Int): List<String> {
  val lineRange = lineRange(line)
  return highlightInfos()
    .filter { it.isSemanticToken() && it.startOffset in lineRange }
    .map { "${positionOf(it.startOffset)}-${positionOf(it.endOffset)} ${it.forcedTextAttributesKey?.externalName}" }
    .toList()
}

private fun Editor.inlayList(line: Int): List<String> {
  val lineRange = lineRange(line)
  return inlayModel.getInlineElementsInRange(lineRange.first, lineRange.last).map { positionOf(it.offset) }
}

private fun Editor.documentHighlights(): List<HighlightInfo> =
  highlightInfos()
    .filter { it.type == HighlightInfoType.ELEMENT_UNDER_CARET_READ || it.type == HighlightInfoType.ELEMENT_UNDER_CARET_WRITE }
    .toList()

private fun Editor.documentHighlightList(): String {
  val highlights = documentHighlights().map { "${positionOf(it.startOffset)}-${positionOf(it.endOffset)}" }
  return "$highlights, and the caret is at ${positionOf(caretModel.offset)}"
}

private data class CodeLens(val line: Int, val title: String) {
  override fun toString(): String = "$line: $title"
}

/** The text entries of Code Vision in the editor, with the lines of their inlays. */
private fun Editor.codeLenses(): List<CodeLens> {
  val end = document.textLength
  val inlays = inlayModel.getBlockElementsInRange(0, end) +
               inlayModel.getInlineElementsInRange(0, end) +
               inlayModel.getAfterLineEndElementsInRange(0, end)
  return inlays.flatMap { inlay ->
    val entries = inlay.getUserData(CodeVisionListData.KEY)?.visibleLens.orEmpty()
    val line = document.getLineNumber(inlay.offset) + 1
    entries.filterIsInstance<TextCodeVisionEntry>().filter { it !is PlaceholderCodeVisionEntry }.map { CodeLens(line, it.text) }
  }
}

private fun Editor.foldRegionList(): List<String> =
  foldingModel.allFoldRegions.filter { it.isValid }.map { positionOf(it.startOffset) }

/** The offsets of the line, which starts with 1, from its start to its end. */
private fun Editor.lineRange(line: Int): IntRange {
  requireLine(line)
  return document.getLineStartOffset(line - 1)..document.getLineEndOffset(line - 1)
}

private fun Editor.requireLine(line: Int) {
  require(line in 1..document.lineCount) { "Line $line is out of the ${document.lineCount} lines of ${virtualFile?.name}" }
}
