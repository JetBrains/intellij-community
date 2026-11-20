// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.editing

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.PerformanceUnitTest
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import org.jetbrains.yaml.psi.YAMLFile

/**
 * Measures the typing in a large OpenAPI file, see IJPL-63225.
 * Each attempt types characters in the editor and commits the document after each character.
 * So it measures the editor highlighter update, the typed handlers and the reparse of the PSI.
 * The highlighter tests insert chars without a commit, so they measure only the editor highlighter update.
 * Most tests type in the middle item of the file.
 * The flat-mapping, flat-sequence and long-line tests type in the middle line of a file with one root key over all of the file.
 * There, a block or leaf check costs more than a full reparse, so the reparse must fall back to a full reparse.
 */
@PerformanceUnitTest
class YamlTypingPerformanceTest : BasePlatformTestCase() {
  @PerformanceUnitTest
  fun testTypingInDoubleQuotedScalar() = doTest("description: \"Returns the item $MIDDLE_ITEM", "x".repeat(10))

  @PerformanceUnitTest
  fun testTypingInPlainScalar() = doTest("x-note: plain text of the item $MIDDLE_ITEM", "x".repeat(10))

  @PerformanceUnitTest
  fun testTypingInComment() = doTest("# The comment of the operation $MIDDLE_ITEM", "x".repeat(10))

  @PerformanceUnitTest
  fun testTypingWordsInPlainScalar() = doTest("x-note: plain text of the item $MIDDLE_ITEM", " word".repeat(2), atLineEnd = true)

  @PerformanceUnitTest
  fun testTypingInKey() = doTest("      operation", "x".repeat(10))

  @PerformanceUnitTest
  fun testTypingInBlockScalar() = doTest("block text of the item $MIDDLE_ITEM", "x".repeat(10))

  @PerformanceUnitTest
  fun testTypingInFlowMappingValue() = doTest("x-flow: {first: one", "x".repeat(10))

  @PerformanceUnitTest
  fun testEnterAfterPlainScalar() = doTest("x-note: plain text of the item $MIDDLE_ITEM", "\n".repeat(10), atLineEnd = true)

  @PerformanceUnitTest
  fun testTypingInKeyInSmallFile() = doTest("      operation", "x".repeat(10), items = SMALL_FILE_ITEMS)

  @PerformanceUnitTest
  fun testTypingInPlainScalarInSmallFile() = doTest("x-note: plain text of the item $SMALL_MIDDLE_ITEM", "x".repeat(10), items = SMALL_FILE_ITEMS)

  @PerformanceUnitTest
  fun testEnterAfterPlainScalarInSmallFile() =
    doTest("x-note: plain text of the item $SMALL_MIDDLE_ITEM", "\n".repeat(10), atLineEnd = true, items = SMALL_FILE_ITEMS)

  @PerformanceUnitTest
  fun testTypingInKeyInFlatMapping() =
    doTest("  key_$FLAT_MIDDLE_LINE", "x".repeat(10), text = flatMappingText(), markerStart = "en:\n", warmups = FLAT_WARMUPS)

  @PerformanceUnitTest
  fun testEnterInFlatMapping() =
    doTest("  key_$FLAT_MIDDLE_LINE", "\n".repeat(10), atLineEnd = true, text = flatMappingText(), markerStart = "en:\n",
           warmups = FLAT_WARMUPS)

  @PerformanceUnitTest
  fun testOpenBracketInFlatMappingValue() =
    doTest("  key_$FLAT_MIDDLE_LINE: ", "[".repeat(10), text = flatMappingText(), markerStart = "en:\n", warmups = FLAT_WARMUPS)

  @PerformanceUnitTest
  fun testEnterInFlatSequence() =
    doTest("  - item $FLAT_MIDDLE_LINE", "\n".repeat(10), atLineEnd = true, text = flatSequenceText(), markerStart = "items:\n",
           warmups = FLAT_WARMUPS)

  @PerformanceUnitTest
  fun testTypingInLongPlainLine() =
    doTest("word$LONG_LINE_MIDDLE_WORD", "x".repeat(10), text = longPlainLineText(), markerStart = "data: ", warmups = FLAT_WARMUPS)

  @PerformanceUnitTest
  fun testHighlighterUpdateInDoubleQuotedScalar() = doHighlighterTest("description: \"Returns the item $MIDDLE_ITEM")

  @PerformanceUnitTest
  fun testHighlighterUpdateInKey() = doHighlighterTest("      operation")

  @PerformanceUnitTest
  fun testHighlighterUpdateInBlockScalar() = doHighlighterTest("block text of the item $MIDDLE_ITEM")

  /**
   * Inserts 10 chars after [caretMarker] in the middle item, one char at a time, and does not commit the document.
   * So it measures only the update of the editor highlighter, which relexes the text from a restart point of the lexer.
   */
  private fun doHighlighterTest(caretMarker: String) {
    myFixture.configureByText("openapi.yaml", openApiText())
    assertInstanceOf(myFixture.editor.highlighter, LexerEditorHighlighter::class.java)
    val document = myFixture.editor.document
    var offset = 0

    Benchmark.newBenchmark(getTestName(false)) {
      repeat(10) { i ->
        WriteCommandAction.runWriteCommandAction(project) { document.insertString(offset + i, "x") }
      }
    }.setup {
      offset = markerEnd(document.charsSequence, caretMarker, itemStart(MIDDLE_ITEM))
    }.start()
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
  ) {
    myFixture.configureByText("openapi.yaml", text)
    assertInstanceOf(myFixture.file, YAMLFile::class.java)
    val document = myFixture.editor.document

    Benchmark.newBenchmark(getTestName(false)) {
      for (char in typedText) {
        myFixture.type(char)
        PsiDocumentManager.getInstance(project).commitDocument(document)
      }
    }.setup {
      val chars = document.charsSequence
      val markerEnd = markerEnd(chars, caretMarker, markerStart)
      myFixture.editor.caretModel.moveToOffset(if (atLineEnd) chars.indexOf('\n', markerEnd) else markerEnd)
    }.warmupIterations(warmups).start()
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

/** Builds an OpenAPI file with [items] items. The default file has about 2 MB, below the size limit of the code insight. */
private fun openApiText(items: Int = ITEMS): String = buildString {
  append("openapi: \"3.0.3\"\n")
  append("paths:\n")
  for (i in 0 until items) {
    append("  /items/$i:\n")
    append("    get:\n")
    append("      summary: \"Get the item $i\"\n")
    append("      description: \"Returns the item $i with all its fields. It is a long text of the description.\"\n")
    append("      # The comment of the operation $i\n")
    append("      operationId: get-item-$i\n")
    append("      x-note: plain text of the item $i\n")
    append("      x-text: |\n")
    append("        block text of the item $i\n")
    append("      x-flow: {first: one, second: two}\n")
    append("      responses:\n")
    append("        \"200\":\n")
    append("          description: OK\n")
  }
}

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
