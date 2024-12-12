// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.reworked

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jediterm.terminal.TextStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.plugins.terminal.JBTerminalSystemSettingsProvider
import org.jetbrains.plugins.terminal.block.output.HighlightingInfo
import org.jetbrains.plugins.terminal.block.output.TerminalOutputHighlightingsSnapshot
import org.jetbrains.plugins.terminal.block.output.TextStyleAdapter
import org.jetbrains.plugins.terminal.block.reworked.TerminalModel
import org.jetbrains.plugins.terminal.block.session.StyleRange
import org.jetbrains.plugins.terminal.block.ui.BlockTerminalColorPalette
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
internal class TerminalModelTest : BasePlatformTestCase() {
  override fun runInDispatchThread(): Boolean = false

  @Test
  fun `update editor content`() = runBlocking(Dispatchers.EDT) {
    val model = createTerminalModel()

    val text = """
      123 sdfsdf sdfsdf
      234234234324 dsfsdfsdfsdf
      2342341 adfasfasfa asdsdasd
      
      asdasdas
      
    """.trimIndent()

    model.updateEditor(0, text, emptyList())

    assertEquals(text, model.editor.document.text)
  }

  @Test
  fun `update editor content incrementally with styles`() = runBlocking(Dispatchers.EDT) {
    val model = createTerminalModel()

    val text1 = """
      first line
      second line
    """.trimIndent()
    val styles1 = listOf(styleRange(0, 5), styleRange(11, 17), styleRange(18, 22))

    val text2 = """
      replaced second line
      third line
    """.trimIndent()
    val styles2 = listOf(styleRange(0, 8), styleRange(9, 15), styleRange(21, 26))

    model.updateEditor(0, text1, styles1)
    model.updateEditor(1, text2, styles2)

    val expectedText = """
      first line
      replaced second line
      third line
    """.trimIndent()
    val expectedHighlightings = listOf(highlighting(0, 5), highlighting(11, 19), highlighting(20, 26), highlighting(32, 37))
    val expectedHighlightingsSnapshot = TerminalOutputHighlightingsSnapshot(model.editor.document, expectedHighlightings)

    assertEquals(expectedText, model.editor.document.text)
    assertEquals(expectedHighlightingsSnapshot, model.highlightingsModel.getHighlightingsSnapshot())
  }

  @Test
  fun `update editor content incrementally with overflow`() = runBlocking(Dispatchers.EDT) {
    val model = createTerminalModel(maxLength = 16)

    val text1 = """
      foofoo
      barbar
    """.trimIndent()
    val styles1 = listOf(styleRange(0, 3), styleRange(3, 6), styleRange(7, 10), styleRange(10, 13))

    val text2 = """
      bazbaz
      badbad
    """.trimIndent()
    val styles2 = listOf(styleRange(0, 6), styleRange(7, 13))

    model.updateEditor(0, text1, styles1)
    model.updateEditor(1, text2, styles2)

    val expectedText = """
      oo
      bazbaz
      badbad
    """.trimIndent()
    val expectedHighlightings = listOf(highlighting(3, 9), highlighting(10, 16))
    val expectedHighlightingsSnapshot = TerminalOutputHighlightingsSnapshot(model.editor.document, expectedHighlightings)

    assertEquals(expectedText, model.editor.document.text)
    assertEquals(expectedHighlightingsSnapshot, model.highlightingsModel.getHighlightingsSnapshot())
  }

  @Test
  fun `update editor content after overflow`() = runBlocking(Dispatchers.EDT) {
    val model = createTerminalModel(maxLength = 16)

    val text1 = """
      foofoo
      barbar
    """.trimIndent()
    val styles1 = listOf(styleRange(0, 3), styleRange(3, 6), styleRange(7, 10), styleRange(10, 13))

    val text2 = """
      bazbaz
      badbad
    """.trimIndent()
    val styles2 = listOf(styleRange(0, 6), styleRange(7, 13))

    val text3 = """
      fadfad
      kadkad
    """.trimIndent()
    val styles3 = listOf(styleRange(0, 6), styleRange(7, 13))

    model.updateEditor(0, text1, styles1)
    model.updateEditor(1, text2, styles2)
    model.updateEditor(2, text3, styles3)

    val expectedText = """
      az
      fadfad
      kadkad
    """.trimIndent()
    val expectedHighlightings = listOf(highlighting(3, 9), highlighting(10, 16))
    val expectedHighlightingsSnapshot = TerminalOutputHighlightingsSnapshot(model.editor.document, expectedHighlightings)

    assertEquals(expectedText, model.editor.document.text)
    assertEquals(expectedHighlightingsSnapshot, model.highlightingsModel.getHighlightingsSnapshot())
  }

  private fun createTerminalModel(maxLength: Int = 0): TerminalModel {
    val document = EditorFactory.getInstance().createDocument("")
    val editor = EditorFactory.getInstance().createEditor(document, project) as EditorEx
    Disposer.register(testRootDisposable) {
      EditorFactory.getInstance().releaseEditor(editor)
    }

    return TerminalModel(editor, JBTerminalSystemSettingsProvider(), maxLength)
  }

  private suspend fun TerminalModel.updateEditor(absoluteLineIndex: Int, text: String, styles: List<StyleRange>) {
    writeAction {
      updateEditorContent(absoluteLineIndex, text, styles)
    }
  }

  private fun styleRange(start: Int, end: Int): StyleRange {
    return StyleRange(start, end, TextStyle())
  }

  private val colorPalette = BlockTerminalColorPalette()

  private fun highlighting(start: Int, end: Int): HighlightingInfo {
    return HighlightingInfo(start, end, TextStyleAdapter(TextStyle(), colorPalette))
  }
}