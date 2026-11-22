// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.editing

import com.intellij.openapi.application.EDT
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightProjectFixture
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.PerformanceUnitTest
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.StressTestApplication
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.yaml.psi.YAMLFile
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

/**
 * Measures the typing in a large OpenAPI file, see IJPL-63225.
 * Each attempt types characters in the editor and commits the document after each character.
 * So it measures the editor highlighter update, the typed handlers and the reparse of the PSI.
 * The highlighter tests insert chars without a commit, so they measure only the editor highlighter update.
 * Most tests type in the middle item of the file.
 * The flat-mapping, flat-sequence and long-line tests type in the middle line of a file with one root key over all of the file.
 * There, a block or leaf check costs more than a full reparse, so the reparse must fall back to a full reparse.
 * The small-file and medium-file tests check that the reparse checks cost little in a file of a usual size.
 * The backslash tests type a backslash in a plain scalar.
 * A backslash is an escape only in a double-quoted scalar, so the leaf check accepts it when it does not escape a double quote.
 */
@StressTestApplication
@TestApplication
@PerformanceUnitTest
class YamlTypingPerformanceTest : YamlPerformanceTestCase() {
  private val projectFixture = codeInsightProjectFixture()
  private val myFixture by codeInsightFixture(projectFixture)

  @Test
  fun testTypingInDoubleQuotedScalar() = doTest("description: \"Returns the item $MIDDLE_ITEM", "x".repeat(10))

  @Test
  fun testTypingInPlainScalar() = doTest("x-note: plain text of the item $MIDDLE_ITEM", "x".repeat(10))

  @Test
  fun testTypingInComment() = doTest("# The comment of the operation $MIDDLE_ITEM", "x".repeat(10))

  @Test
  fun testTypingWordsInPlainScalar() = doTest("x-note: plain text of the item $MIDDLE_ITEM", " word".repeat(2), atLineEnd = true)

  @Test
  fun testTypingInKey() = doTest("      operation", "x".repeat(10))

  @Test
  fun testTypingInBlockScalar() = doTest("block text of the item $MIDDLE_ITEM", "x".repeat(10))

  @Test
  fun testTypingInFlowMappingValue() = doTest("x-flow: {first: one", "x".repeat(10))

  @Test
  fun testEnterAfterPlainScalar() = doTest("x-note: plain text of the item $MIDDLE_ITEM", "\n".repeat(10), atLineEnd = true)

  @Test
  fun testTypingInKeyInSmallFile() = doTest("      operation", "x".repeat(10), items = SMALL_FILE_ITEMS)

  @Test
  fun testTypingInPlainScalarInSmallFile() = doTest("x-note: plain text of the item $SMALL_MIDDLE_ITEM", "x".repeat(10), items = SMALL_FILE_ITEMS)

  @Test
  fun testEnterAfterPlainScalarInSmallFile() =
    doTest("x-note: plain text of the item $SMALL_MIDDLE_ITEM", "\n".repeat(10), atLineEnd = true, items = SMALL_FILE_ITEMS)

  @Test
  fun testTypingInDoubleQuotedScalarInSmallFile() =
    doTest("description: \"Returns the item $SMALL_MIDDLE_ITEM", "x".repeat(10), items = SMALL_FILE_ITEMS)

  @Test
  fun testTypingInCommentInSmallFile() =
    doTest("# The comment of the operation $SMALL_MIDDLE_ITEM", "x".repeat(10), items = SMALL_FILE_ITEMS)

  @Test
  fun testTypingInBlockScalarInSmallFile() = doTest("block text of the item $SMALL_MIDDLE_ITEM", "x".repeat(10), items = SMALL_FILE_ITEMS)

  @Test
  fun testTypingInFlowMappingValueInSmallFile() = doTest("x-flow: {first: one", "x".repeat(10), items = SMALL_FILE_ITEMS)

  @Test
  fun testTypingBackslashInPlainScalarInSmallFile() =
    doTest("x-note: plain text of the item $SMALL_MIDDLE_ITEM", "\\".repeat(10), items = SMALL_FILE_ITEMS)

  @Test
  fun testTypingBackslashInPlainScalar() = doTest("x-note: plain text of the item $MIDDLE_ITEM", "\\".repeat(10))

  @Test
  fun testTypingBackslashInPlainScalarInMediumFile() =
    doTest("x-note: plain text of the item $MEDIUM_MIDDLE_ITEM", "\\".repeat(10), items = MEDIUM_FILE_ITEMS)

  @Test
  fun testTypingInKeyInMediumFile() = doTest("      operation", "x".repeat(10), items = MEDIUM_FILE_ITEMS)

  @Test
  fun testTypingInPlainScalarInMediumFile() =
    doTest("x-note: plain text of the item $MEDIUM_MIDDLE_ITEM", "x".repeat(10), items = MEDIUM_FILE_ITEMS)

  @Test
  fun testEnterAfterPlainScalarInMediumFile() =
    doTest("x-note: plain text of the item $MEDIUM_MIDDLE_ITEM", "\n".repeat(10), atLineEnd = true, items = MEDIUM_FILE_ITEMS)

  @Test
  fun testTypingInKeyInFlatMapping() =
    doTest("  key_$FLAT_MIDDLE_LINE", "x".repeat(10), text = flatMappingText(), markerStart = "en:\n", warmups = FLAT_WARMUPS)

  @Test
  fun testEnterInFlatMapping() =
    doTest("  key_$FLAT_MIDDLE_LINE", "\n".repeat(10), atLineEnd = true, text = flatMappingText(), markerStart = "en:\n",
           warmups = FLAT_WARMUPS)

  @Test
  fun testOpenBracketInFlatMappingValue() =
    doTest("  key_$FLAT_MIDDLE_LINE: ", "[".repeat(10), text = flatMappingText(), markerStart = "en:\n", warmups = FLAT_WARMUPS)

  @Test
  fun testEnterInFlatSequence() =
    doTest("  - item $FLAT_MIDDLE_LINE", "\n".repeat(10), atLineEnd = true, text = flatSequenceText(), markerStart = "items:\n",
           warmups = FLAT_WARMUPS)

  @Test
  fun testTypingInLongPlainLine() =
    doTest("word$LONG_LINE_MIDDLE_WORD", "x".repeat(10), text = longPlainLineText(), markerStart = "data: ", warmups = FLAT_WARMUPS)

  @Test
  fun testHighlighterUpdateInDoubleQuotedScalar() = doHighlighterTest("description: \"Returns the item $MIDDLE_ITEM")

  @Test
  fun testHighlighterUpdateInKey() = doHighlighterTest("      operation")

  @Test
  fun testHighlighterUpdateInBlockScalar() = doHighlighterTest("block text of the item $MIDDLE_ITEM")

  /**
   * Inserts 10 chars after [caretMarker] in the middle item, one char at a time, and does not commit the document.
   * So it measures only the update of the editor highlighter, which relexes the text from a restart point of the lexer.
   */
  private fun doHighlighterTest(caretMarker: String) = onEdt {
    myFixture.configureByText("openapi.yaml", openApiText(ITEMS))
    assertInstanceOf(LexerEditorHighlighter::class.java, myFixture.editor.highlighter)
    val document = myFixture.editor.document
    var offset = 0

    benchmark {
      repeat(10) { i ->
        WriteCommandAction.runWriteCommandAction(myFixture.project) { document.insertString(offset + i, "x") }
      }
    }.setup {
      offset = markerEnd(document.charsSequence, caretMarker, itemStart(MIDDLE_ITEM))
    }.startForTest()
  }

  /**
   * Places the caret after [caretMarker] and types [typedText], one char at a time.
   * With [atLineEnd], the caret goes to the end of the line of [caretMarker].
   * So the text of each attempt goes after the text of the previous attempts.
   * The document is committed after each char.
   * [items] sets the size of the OpenAPI file.
   * [text] is the file text, and [caretMarker] is searched after the first match of [markerStart] in it.
   * [warmups] is the number of warm-up attempts before the measured attempts.
   */
  private fun doTest(
    caretMarker: String,
    typedText: String,
    atLineEnd: Boolean = false,
    items: Int = ITEMS,
    text: String = openApiText(items),
    markerStart: String = itemStart(items / 2),
    warmups: Int = 1,
  ) = onEdt {
    myFixture.configureByText("openapi.yaml", text)
    assertInstanceOf(YAMLFile::class.java, myFixture.file)
    val document = myFixture.editor.document

    benchmark {
      for (char in typedText) {
        myFixture.type(char)
        PsiDocumentManager.getInstance(myFixture.project).commitDocument(document)
      }
    }.setup {
      val chars = document.charsSequence
      val markerEnd = markerEnd(chars, caretMarker, markerStart)
      myFixture.editor.caretModel.moveToOffset(if (atLineEnd) chars.indexOf('\n', markerEnd) else markerEnd)
    }.warmupIterations(warmups).startForTest()
  }

  /**
   * Runs [action] on the EDT, as the editor and the typing need it.
   * One test of a large file can type for some minutes, so the timeout is long.
   */
  private fun onEdt(action: () -> Unit) {
    timeoutRunBlocking(timeout = 10.minutes) {
      withContext(Dispatchers.EDT) { action() }
    }
  }
}

/** Returns the offset after the first [marker] that follows the first match of [anchor]. */
private fun markerEnd(text: CharSequence, marker: String, anchor: String): Int {
  val anchorStart = text.indexOf(anchor)
  val markerStart = text.indexOf(marker, anchorStart)
  check(anchorStart >= 0 && markerStart >= 0) { "no '$marker' after '$anchor'" }
  return markerStart + marker.length
}

/** Returns the first line of the item [item] of the OpenAPI file. */
private fun itemStart(item: Int): String = "  /items/$item:\n"

/** The items of the OpenAPI file of about 2 MB, below the size limit of the code insight. */
private const val ITEMS = 5000
private const val MIDDLE_ITEM = ITEMS / 2

/** The lines of the flat mapping and of the flat sequence, about 1 MB and 400 KB. */
private const val FLAT_LINES = 30_000
private const val FLAT_MIDDLE_LINE = FLAT_LINES / 2

/** The words of the long plain scalar line, about 490 KB. */
private const val LONG_LINE_WORDS = 50_000
private const val LONG_LINE_MIDDLE_WORD = LONG_LINE_WORDS / 2

/**
 * The warm-up attempts of the flat-mapping, flat-sequence and long-line tests.
 * The earlier tests of the class warm the full reparse only with the reparse registry keys off.
 * So with 1 warm-up attempt, these tests look slower with the keys on.
 * The other tests keep 1 warm-up attempt, so their dashboard history stays comparable.
 */
private const val FLAT_WARMUPS = 4

/** The items of a small file of about 4 KB. */
private const val SMALL_FILE_ITEMS = 10
private const val SMALL_MIDDLE_ITEM = SMALL_FILE_ITEMS / 2

/** The items of a medium file of about 100 KB. */
private const val MEDIUM_FILE_ITEMS = 250
private const val MEDIUM_MIDDLE_ITEM = MEDIUM_FILE_ITEMS / 2

/** Builds a file with one root key over all of the file, such as a Rails i18n file. */
private fun flatMappingText(): String = buildString {
  append("en:\n")
  for (i in 0 until FLAT_LINES) {
    append("  key_$i: \"Value of the key $i\"\n")
  }
}

/** Builds a file with one root key and a flat sequence over all of the file. */
private fun flatSequenceText(): String = buildString {
  append("items:\n")
  for (i in 0 until FLAT_LINES) {
    append("  - item $i\n")
  }
}

/** Builds a file with one plain scalar line over all of the file. */
private fun longPlainLineText(): String = buildString {
  append("data: ")
  (0 until LONG_LINE_WORDS).joinTo(this, " ") { "word$it" }
  append("\n")
}
