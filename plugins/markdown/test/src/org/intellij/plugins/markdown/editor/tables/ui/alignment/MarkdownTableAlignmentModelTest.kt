// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.tables.ui.alignment

import com.intellij.openapi.editor.impl.view.FontLayoutService
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import com.intellij.testFramework.MockFontLayoutService
import com.intellij.markdown.frontend.editor.tables.ui.alignment.MarkdownTableAlignmentController
import org.intellij.plugins.markdown.editor.tables.ui.MarkdownTableInlayProvider
import org.intellij.plugins.markdown.settings.MarkdownCodeInsightSettings

@Suppress("MarkdownIncorrectTableFormatting", "MarkdownNoTableBorders")
class MarkdownTableAlignmentModelTest : LightPlatformCodeInsightTestCase() {
  override fun setUp() {
    super.setUp()
    FontLayoutService.setInstance(MockFontLayoutService(CHAR_WIDTH, LINE_HEIGHT, DESCENT))
  }

  override fun tearDown() {
    try {
      FontLayoutService.setInstance(null)
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  fun `test padding aligns separators of a ragged table`() {
    configureRaggedTable()
    assertFalse("the table should not be aligned before the model runs", bordersAligned())
    model.refresh(TextRange(0, editor.document.textLength))
    assertBordersAligned()
  }

  fun `test padding aligns separators when cell widths differ only in pixels`() {
    FontLayoutService.setInstance(wideCharAwareFontService())
    // Every column holds the same number of characters, so a character-counting formatter considers this
    // table aligned; the wide character makes it misaligned on screen. This is the case that the editor-wide
    // character grid mode used to work around.
    // language=Markdown
    configureFromFileText(
      "some.md",
      """
      | $WIDE_CHAR  | bb |
      |----|----|
      | cc | dd |
      """.trimIndent()
    )
    assertFalse("the table should not be aligned before the model runs", bordersAligned())
    model.refresh(TextRange(0, editor.document.textLength))
    assertBordersAligned()
  }

  fun `test padding aligns separators around a collapsed fold region`() {
    // language=Markdown
    configureFromFileText(
      "some.md",
      """
      | abcdefgh | bb |
      |---|----|
      | cc | dd |
      """.trimIndent()
    )
    val start = editor.document.text.indexOf("abcdefgh")
    editor.foldingModel.runBatchFoldingOperation {
      val region = requireNotNull(editor.foldingModel.addFoldRegion(start, start + 8, "..."))
      region.isExpanded = false
    }
    model.refresh(TextRange(0, editor.document.textLength))
    assertBordersAligned()

    editor.foldingModel.runBatchFoldingOperation {
      requireNotNull(editor.foldingModel.getFoldRegion(start, start + 8)).isExpanded = true
    }
    model.refresh(TextRange(0, editor.document.textLength))
    assertBordersAligned()
  }

  fun `test a second refresh changes nothing`() {
    configureRaggedTable()
    model.refresh(TextRange(0, editor.document.textLength))
    val first = padSnapshot()
    model.refresh(TextRange(0, editor.document.textLength))
    assertEquals(first, padSnapshot())
    assertBordersAligned()
  }

  fun `test right alignment puts the padding in front of the content`() {
    // language=Markdown
    configureFromFileText(
      "some.md",
      """
      | a | bb |
      |--:|----|
      | ccc | d |
      """.trimIndent()
    )
    model.refresh(TextRange(0, editor.document.textLength))
    val headerCellStart = 1
    val pads = editor.inlayModel.getInlineElementsInRange(headerCellStart, headerCellStart, TablePadRenderer::class.java)
    assertEquals("the header's first cell should be padded on its leading side", 1, pads.size)
    assertBordersAligned()
  }

  fun `test existing padding is dropped when the setting goes off`() {
    configureRaggedTable()
    model.refresh(TextRange(0, editor.document.textLength))
    assertNotEmpty(allPads())
    withSettingDisabled {
    model.refresh(TextRange(0, editor.document.textLength))
      assertNoPads()
    }
  }

  fun `test a table can soft wrap when visual alignment is disabled`() {
    configureRaggedTable()
    withSettingDisabled {
      EditorTestUtil.configureSoftWraps(editor, 6)
    model.refresh(TextRange(0, editor.document.textLength))

      assertNotEmpty(editor.softWrapModel.getSoftWrapsForRange(0, editor.document.textLength))
      assertNoPads()
    }
  }

  fun `test changing the visual alignment setting recalculates table soft wraps`() {
    configureRaggedTable()
    EditorTestUtil.configureSoftWraps(editor, 6)
    assertEmpty(editor.softWrapModel.getSoftWrapsForRange(0, editor.document.textLength))
    MarkdownTableAlignmentController(editor).also {
      Disposer.register(testRootDisposable, it)
      it.start()
    }

    val state = MarkdownCodeInsightSettings.getInstance().state
    val previous = state.alignTableCellsVisually
    try {
      state.alignTableCellsVisually = false
      MarkdownTableAlignmentSettingsListener.fireChanged()

      assertNotEmpty(editor.softWrapModel.getSoftWrapsForRange(0, editor.document.textLength))
    }
    finally {
      state.alignTableCellsVisually = previous
      MarkdownTableAlignmentSettingsListener.fireChanged()
    }
  }

  fun `test no padding is added while table inlays are disabled for the editor`() {
    configureRaggedTable()
    editor.putUserData(MarkdownTableInlayProvider.DISABLE_TABLE_INLAYS, true)
    EditorTestUtil.configureSoftWraps(editor, 6)
    model.refresh(TextRange(0, editor.document.textLength))

    assertNotEmpty(editor.softWrapModel.getSoftWrapsForRange(0, editor.document.textLength))
    assertNoPads()
  }

  fun `test a table without borders is left alone`() {
    // language=Markdown
    configureFromFileText(
      "some.md",
      """
      a | bb
      ---|----
      ccc | d
      """.trimIndent()
    )
    model.refresh(TextRange(0, editor.document.textLength))
    assertNoPads()
  }

  fun `test a table over the row limit is left alone`() {
    val text = buildString {
      append("| a | bb |\n|---|----|\n")
      repeat(MarkdownTableAlignmentModel.MAX_ALIGNED_ROWS) { append("| ccc | d |\n") }
    }
    configureFromFileText("some.md", text)
    model.refresh(TextRange(0, editor.document.textLength))
    assertNoPads()
  }

  fun `test a table is found from a range inside a block quote`() {
    // language=Markdown
    configureFromFileText(
      "some.md",
      """
      > | a | bb |
      > |---|----|
      > | ccc | d |
      """.trimIndent()
    )
    val offset = editor.document.text.indexOf("ccc")
    model.refresh(TextRange(offset, offset + 1))
    assertNotEmpty(allPads())
  }

  fun `test Markdown wrap strategy keeps a table too wide for the editor on one line`() {
    configureRaggedTable()
    EditorTestUtil.configureSoftWraps(editor, 6)
    model.refresh(TextRange(0, editor.document.textLength))
    assertNotEmpty(allPads())
    assertEmpty("the table should have been kept off the soft wrap machinery",
                editor.softWrapModel.getSoftWrapsForRange(0, editor.document.textLength))
    assertBordersAligned()
  }

  fun `test disposing the model removes all padding`() {
    configureRaggedTable()
    val model = MarkdownTableAlignmentModel(editor)
    try {
    model.refresh(TextRange(0, editor.document.textLength))
      assertNotEmpty(allPads())
    }
    finally {
      Disposer.dispose(model)
    }
    assertNoPads()
  }

  private fun configureRaggedTable() {
    // language=Markdown
    configureFromFileText(
      "some.md",
      """
      | a | bb |
      |---|----|
      | ccc | d |
      """.trimIndent()
    )
  }

  private val model: MarkdownTableAlignmentModel by lazy {
    MarkdownTableAlignmentModel(editor).also { Disposer.register(testRootDisposable, it) }
  }

  private fun withSettingDisabled(action: () -> Unit) {
    val state = MarkdownCodeInsightSettings.getInstance().state
    val previous = state.alignTableCellsVisually
    state.alignTableCellsVisually = false
    try {
      action()
    }
    finally {
      state.alignTableCellsVisually = previous
    }
  }

  private fun wideCharAwareFontService(): MockFontLayoutService {
    return MockFontLayoutService(
      { codePoint -> if (codePoint == WIDE_CHAR.code) 2.0 * CHAR_WIDTH else CHAR_WIDTH.toDouble() },
      LINE_HEIGHT,
      DESCENT,
    )
  }

  private fun allPads() =
    editor.inlayModel.getInlineElementsInRange(0, editor.document.textLength, TablePadRenderer::class.java)

  private fun padSnapshot() = allPads().map { it.offset to it.widthInPixels }

  private fun assertNoPads() = assertEmpty(allPads())

  private fun renderedX(offset: Int): Int {
    return editor.offsetToXY(offset, false, false).x +
           editor.inlayModel.getInlineElementsInRange(offset, offset).sumOf { it.widthInPixels }
  }

  private fun bordersAligned(): Boolean = misalignedSeparator() == null

  private fun assertBordersAligned() {
    val complaint = misalignedSeparator()
    if (complaint != null) {
      fail(complaint)
    }
  }

  private fun misalignedSeparator(): String? {
    val document = editor.document
    val text = document.charsSequence
    val bordersByRow = (0 until document.lineCount)
      .map { line ->
        (document.getLineStartOffset(line) until document.getLineEndOffset(line)).filter { text[it] == '|' }
      }
      .filter { it.size >= 2 }
    if (bordersByRow.isEmpty()) {
      return "no table rows found"
    }
    val separatorCount = bordersByRow.minOf { it.size }
    for (index in 0 until separatorCount) {
      val xs = bordersByRow.map { renderedX(it[index]) }
      if (xs.distinct().size != 1) {
        return "separator #$index is drawn at different x in different rows: $xs"
      }
    }
    return null
  }

  private companion object {
    const val CHAR_WIDTH = 10
    const val LINE_HEIGHT = 10
    const val DESCENT = 2
    const val WIDE_CHAR = '中'
  }
}
