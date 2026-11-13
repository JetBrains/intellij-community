// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.tables.ui.alignment

import com.intellij.markdown.frontend.editor.tables.ui.alignment.MarkdownTableAlignmentController
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.impl.view.FontLayoutService
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.MockFontLayoutService
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.intellij.plugins.markdown.editor.tables.ui.MarkdownTableInlayProvider
import org.intellij.plugins.markdown.settings.MarkdownSettings
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

@TestApplication
@Suppress("MarkdownIncorrectTableFormatting", "MarkdownNoTableBorders")
class MarkdownTableAlignmentModelTest {
  private val myFixture by codeInsightModuleFixture(projectFixture)

  @BeforeEach
  fun setUp() {
    FontLayoutService.setInstance(MockFontLayoutService(CHAR_WIDTH, LINE_HEIGHT, DESCENT))
  }

  @AfterEach
  fun tearDown() {
    FontLayoutService.setInstance(null)
  }

  @Test
  fun `test padding aligns separators of a ragged table`(): Unit = timeoutRunBlocking {
    configureRaggedTable()
    withContext(Dispatchers.EDT) {
      assertFalse(bordersAligned(), "the table should not be aligned before the model runs")
      model.refresh(TextRange(0, myFixture.editor.document.textLength))
      assertBordersAligned()
    }
  }

  @Test
  fun `test padding aligns separators when cell widths differ only in pixels`(): Unit = timeoutRunBlocking {
    FontLayoutService.setInstance(wideCharAwareFontService())
    // Every column holds the same number of characters, so a character-counting formatter considers this
    // table aligned; the wide character makes it misaligned on screen. This is the case that the editor-wide
    // character grid mode used to work around.
    // language=Markdown
    myFixture.configureByText(
      "some.md",
      """
      | $WIDE_CHAR  | bb |
      |----|----|
      | cc | dd |
      """.trimIndent()
    )
    withContext(Dispatchers.EDT) {
      assertFalse(bordersAligned(), "the table should not be aligned before the model runs")
      model.refresh(TextRange(0, myFixture.editor.document.textLength))
      assertBordersAligned()
    }
  }

  @Test
  fun `test padding aligns separators around a collapsed fold region`(): Unit = timeoutRunBlocking {
    // language=Markdown
    myFixture.configureByText(
      "some.md",
      """
      | abcdefgh | bb |
      |---|----|
      | cc | dd |
      """.trimIndent()
    )
    withContext(Dispatchers.EDT) {
      val start = myFixture.editor.document.text.indexOf("abcdefgh")
      myFixture.editor.foldingModel.runBatchFoldingOperation {
        val region = requireNotNull(myFixture.editor.foldingModel.addFoldRegion(start, start + 8, "..."))
        region.isExpanded = false
      }
      model.refresh(TextRange(0, myFixture.editor.document.textLength))
      assertBordersAligned()

      myFixture.editor.foldingModel.runBatchFoldingOperation {
        requireNotNull(myFixture.editor.foldingModel.getFoldRegion(start, start + 8)).isExpanded = true
      }
      model.refresh(TextRange(0, myFixture.editor.document.textLength))
      assertBordersAligned()
    }
  }

  @Test
  fun `test a second refresh changes nothing`(): Unit = timeoutRunBlocking {
    configureRaggedTable()
    withContext(Dispatchers.EDT) {
      model.refresh(TextRange(0, myFixture.editor.document.textLength))
      val first = padSnapshot()
      model.refresh(TextRange(0, myFixture.editor.document.textLength))
      assertEquals(first, padSnapshot())
      assertBordersAligned()
    }
  }

  @Test
  fun `test right alignment puts the padding in front of the content`(): Unit = timeoutRunBlocking {
    // language=Markdown
    myFixture.configureByText(
      "some.md",
      """
      | a | bb |
      |--:|----|
      | ccc | d |
      """.trimIndent()
    )
    withContext(Dispatchers.EDT) {
      model.refresh(TextRange(0, myFixture.editor.document.textLength))
      val headerCellStart = 1
      val pads = myFixture.editor.inlayModel.getInlineElementsInRange(headerCellStart, headerCellStart, TablePadRenderer::class.java)
      assertEquals(1, pads.size, "the header's first cell should be padded on its leading side")
      assertBordersAligned()
    }
  }

  @Test
  fun `test existing padding is dropped when the setting goes off`(): Unit = timeoutRunBlocking {
    configureRaggedTable()
    withContext(Dispatchers.EDT) {
      model.refresh(TextRange(0, myFixture.editor.document.textLength))
      assertThat(allPads()).isNotEmpty()
      withSettingDisabled {
      model.refresh(TextRange(0, myFixture.editor.document.textLength))
        assertNoPads()
      }
    }
  }

  @Test
  fun `test a table can soft wrap when visual alignment is disabled`(): Unit = timeoutRunBlocking {
    configureRaggedTable()
    withContext(Dispatchers.EDT) {
      withSettingDisabled {
        EditorTestUtil.configureSoftWraps(myFixture.editor, 6)
      model.refresh(TextRange(0, myFixture.editor.document.textLength))

        assertThat(myFixture.editor.softWrapModel.getSoftWrapsForRange(0, myFixture.editor.document.textLength)).isNotEmpty()
        assertNoPads()
      }
    }
  }

  @Test
  fun `test changing the visual alignment setting recalculates table soft wraps`(): Unit = timeoutRunBlocking {
    configureRaggedTable()
    withContext(Dispatchers.EDT) {
      EditorTestUtil.configureSoftWraps(myFixture.editor, 6)
      assertThat(myFixture.editor.softWrapModel.getSoftWrapsForRange(0, myFixture.editor.document.textLength)).isEmpty()
      MarkdownTableAlignmentController(myFixture.editor).also {
        Disposer.register(myFixture.testRootDisposable, it)
        it.start()
      }

      val settings = MarkdownSettings.getInstance()
      val previous = settings.alignTableCellsVisually
      try {
        settings.alignTableCellsVisually = false
        MarkdownTableAlignmentSettingsListener.fireChanged()

        assertThat(myFixture.editor.softWrapModel.getSoftWrapsForRange(0, myFixture.editor.document.textLength)).isNotEmpty()
      }
      finally {
        settings.alignTableCellsVisually = previous
        MarkdownTableAlignmentSettingsListener.fireChanged()
      }
    }
  }

  @Test
  fun `test no padding is added while table inlays are disabled for the editor`(): Unit = timeoutRunBlocking {
    configureRaggedTable()
    withContext(Dispatchers.EDT) {
      myFixture.editor.putUserData(MarkdownTableInlayProvider.DISABLE_TABLE_INLAYS, true)
      EditorTestUtil.configureSoftWraps(myFixture.editor, 6)
      model.refresh(TextRange(0, myFixture.editor.document.textLength))

      assertThat(myFixture.editor.softWrapModel.getSoftWrapsForRange(0, myFixture.editor.document.textLength)).isNotEmpty()
      assertNoPads()
    }
  }

  @Test
  fun `test a table without borders is left alone`(): Unit = timeoutRunBlocking {
    // language=Markdown
    myFixture.configureByText(
      "some.md",
      """
      a | bb
      ---|----
      ccc | d
      """.trimIndent()
    )
    withContext(Dispatchers.EDT) {
      model.refresh(TextRange(0, myFixture.editor.document.textLength))
      assertNoPads()
    }
  }

  @Test
  fun `test a table over the row limit is left alone`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val text = buildString {
        append("| a | bb |\n|---|----|\n")
        repeat(MarkdownTableAlignmentModel.MAX_ALIGNED_ROWS) { append("| ccc | d |\n") }
      }
      myFixture.configureByText("some.md", text)
      model.refresh(TextRange(0, myFixture.editor.document.textLength))
      assertNoPads()
    }
  }

  @Test
  fun `test a table is found from a range inside a block quote`(): Unit = timeoutRunBlocking {
    // language=Markdown
    myFixture.configureByText(
      "some.md",
      """
      > | a | bb |
      > |---|----|
      > | ccc | d |
      """.trimIndent()
    )
    withContext(Dispatchers.EDT) {
      val offset = myFixture.editor.document.text.indexOf("ccc")
      model.refresh(TextRange(offset, offset + 1))
      assertThat(allPads()).isNotEmpty()
    }
  }

  @Test
  fun `test Markdown wrap strategy keeps a table too wide for the editor on one line`(): Unit = timeoutRunBlocking {
    configureRaggedTable()
    withContext(Dispatchers.EDT) {
      EditorTestUtil.configureSoftWraps(myFixture.editor, 6)
      model.refresh(TextRange(0, myFixture.editor.document.textLength))
      assertThat(allPads()).isNotEmpty()
      assertThat(myFixture.editor.softWrapModel.getSoftWrapsForRange(0, myFixture.editor.document.textLength))
        .describedAs("the table should have been kept off the soft wrap machinery")
        .isEmpty()
      assertBordersAligned()
    }
  }

  @Test
  fun `test disposing the model removes all padding`(): Unit = timeoutRunBlocking {
    configureRaggedTable()
    withContext(Dispatchers.EDT) {
      val model = MarkdownTableAlignmentModel(myFixture.editor)
      try {
      model.refresh(TextRange(0, myFixture.editor.document.textLength))
        assertThat(allPads()).isNotEmpty()
      }
      finally {
        Disposer.dispose(model)
      }
      assertNoPads()
    }
  }

  private fun configureRaggedTable() {
    // language=Markdown
    myFixture.configureByText(
      "some.md",
      """
      | a | bb |
      |---|----|
      | ccc | d |
      """.trimIndent()
    )
  }

  private val model: MarkdownTableAlignmentModel by lazy {
    MarkdownTableAlignmentModel(myFixture.editor).also { Disposer.register(myFixture.testRootDisposable, it) }
  }

  private fun withSettingDisabled(action: () -> Unit) {
    val settings = MarkdownSettings.getInstance()
    val previous = settings.alignTableCellsVisually
    settings.alignTableCellsVisually = false
    try {
      action()
    }
    finally {
      settings.alignTableCellsVisually = previous
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
    myFixture.editor.inlayModel.getInlineElementsInRange(0, myFixture.editor.document.textLength, TablePadRenderer::class.java)

  private fun padSnapshot() = allPads().map { it.offset to it.widthInPixels }

  private fun assertNoPads() {
    assertThat(allPads()).isEmpty()
  }

  private fun renderedX(offset: Int): Int {
    return myFixture.editor.offsetToXY(offset, false, false).x +
           myFixture.editor.inlayModel.getInlineElementsInRange(offset, offset).sumOf { it.widthInPixels }
  }

  private fun bordersAligned(): Boolean = misalignedSeparator() == null

  private fun assertBordersAligned() {
    val complaint = misalignedSeparator()
    if (complaint != null) {
      fail(complaint)
    }
  }

  private fun misalignedSeparator(): String? {
    val document = myFixture.editor.document
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
    private val projectFixture = projectFixture(openAfterCreation = true)

    const val CHAR_WIDTH = 10
    const val LINE_HEIGHT = 10
    const val DESCENT = 2
    const val WIDE_CHAR = '中'
  }
}
