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
 * Each test types in the middle item of the file.
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
      offset = markerEnd(document.charsSequence, caretMarker)
    }.start()
  }

  /**
   * Places the caret after [caretMarker] in the middle item and types [typedText], one char at a time.
   * With [atLineEnd], the caret goes to the end of the line of [caretMarker], so the text of each attempt goes after the text of the previous attempts.
   * The document is committed after each char.
   */
  private fun doTest(caretMarker: String, typedText: String, atLineEnd: Boolean = false) {
    myFixture.configureByText("openapi.yaml", openApiText())
    assertInstanceOf(myFixture.file, YAMLFile::class.java)
    val document = myFixture.editor.document

    Benchmark.newBenchmark(getTestName(false)) {
      for (char in typedText) {
        myFixture.type(char)
        PsiDocumentManager.getInstance(project).commitDocument(document)
      }
    }.setup {
      val text = document.charsSequence
      val markerEnd = markerEnd(text, caretMarker)
      myFixture.editor.caretModel.moveToOffset(if (atLineEnd) text.indexOf('\n', markerEnd) else markerEnd)
    }.start()
  }
}

/** Returns the offset after [marker] in the middle item. */
private fun markerEnd(text: CharSequence, marker: String): Int {
  val itemStart = text.indexOf("  /items/$MIDDLE_ITEM:\n")
  val markerStart = text.indexOf(marker, itemStart)
  check(itemStart >= 0 && markerStart >= 0) { "no '$marker' in the middle item" }
  return markerStart + marker.length
}

private const val ITEMS = 5000
private const val MIDDLE_ITEM = ITEMS / 2

/** Builds an OpenAPI file of about 2 MB, below the size limit of the code insight. */
private fun openApiText(): String = buildString {
  append("openapi: \"3.0.3\"\n")
  append("paths:\n")
  for (i in 0 until ITEMS) {
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
