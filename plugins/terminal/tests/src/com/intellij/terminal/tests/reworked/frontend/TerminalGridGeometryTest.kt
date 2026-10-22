// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend

import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.view.CharacterGrid
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.frontend.view.impl.TerminalEditorFactory
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.JreHiDpiUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.jetbrains.plugins.terminal.JBTerminalSystemSettingsProvider
import org.jetbrains.plugins.terminal.block.ui.calculateGridGeometry
import org.jetbrains.plugins.terminal.session.TerminalGridSize
import org.jetbrains.plugins.terminal.session.impl.dto.CellPixelSizeDto
import org.jetbrains.plugins.terminal.util.terminalProjectScope
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.awt.BorderLayout
import java.awt.GraphicsConfiguration
import java.awt.GraphicsDevice
import java.awt.Rectangle
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.awt.image.ColorModel
import javax.swing.JPanel
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * The grid geometry that the terminal editor measures: the grid in cells, and one cell in
 * the device pixels of the display the editor is on.
 */
@RunWith(JUnit4::class)
internal class TerminalGridGeometryTest : BasePlatformTestCase() {

  override fun runInDispatchThread(): Boolean = false

  @Test
  fun `geometry is null before the editor has a size`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    val editor = createEditor()
    assertThat(editor.calculateGridGeometry()).isNull()
  }

  @Test
  fun `cell size on a 1x display is the editor's own size`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    withJreHiDpiEnabled {
      val editor = createEditor()
      showOnDisplay(editor, scale = 1.0)
      val grid = setEditorSize(editor, columns = 80, rows = 24)

      val (gridSize, cellSize) = checkNotNull(editor.calculateGridGeometry())
      assertThat(gridSize).isEqualTo(TerminalGridSize(columns = 80, rows = 24))
      assertThat(cellSize).isEqualTo(CellPixelSizeDto(width = grid.charWidth.roundToInt(), height = editor.lineHeight))
    }
  }

  @Test
  fun `cell size on a 2x display is doubled, the grid is not`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    withJreHiDpiEnabled {
      val editor = createEditor()
      showOnDisplay(editor, scale = 2.0)
      val grid = setEditorSize(editor, columns = 100, rows = 30)

      val (gridSize, cellSize) = checkNotNull(editor.calculateGridGeometry())
      assertThat(gridSize).isEqualTo(TerminalGridSize(columns = 100, rows = 30))
      // Scaled before rounding, so a fractional char width does not round twice.
      assertThat(cellSize).isEqualTo(CellPixelSizeDto(width = (grid.charWidth * 2).roundToInt(), height = editor.lineHeight * 2))
    }
  }

  @Test
  fun `cell width follows the column spacing multiplier`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    withJreHiDpiEnabled {
      val editor = createEditor()
      showOnDisplay(editor, scale = 1.0)
      setEditorSize(editor, columns = 80, rows = 24)
      val [singleWidth, singleHeight] = checkNotNull(editor.calculateGridGeometry()).cellSize

      editor.settings.characterGridWidthMultiplier = 1.5f
      val [widenedWidth, widenedHeight] = checkNotNull(editor.calculateGridGeometry()).cellSize

      assertThat(widenedHeight).isEqualTo(singleHeight)
      assertThat(widenedWidth.toDouble()).isCloseTo(singleWidth * 1.5, within(1.0))
    }
  }

  private fun createEditor(): EditorImpl {
    val scope = terminalProjectScope(project).childScope("TerminalOutputEditor")
    Disposer.register(testRootDisposable) { scope.cancel() }
    return TerminalEditorFactory.createOutputEditor(project, JBTerminalSystemSettingsProvider(), scope)
  }

  /** Puts the editor into a container on a display that draws [scale] device pixels per user-space pixel. */
  private fun showOnDisplay(editor: EditorImpl, scale: Double) {
    val configuration = ScaledDisplayConfiguration(scale)
    val display = object : JPanel(BorderLayout()) {
      override fun getGraphicsConfiguration(): GraphicsConfiguration = configuration
    }
    // AWT hands the container's configuration down to the added component and its children.
    display.add(editor.component)
    assertThat(editor.contentComponent.graphicsConfiguration).isSameAs(configuration)
  }

  /** Sizes the editor to [columns] x [rows] cells and returns its character grid. */
  private fun setEditorSize(editor: EditorImpl, columns: Int, rows: Int): CharacterGrid {
    val grid = checkNotNull(editor.characterGrid) { "the terminal editor has no character grid" }
    val widthInPixels = ceil(columns * grid.charWidth).toInt()
    val heightInPixels = rows * editor.lineHeight
    EditorTestUtil.setEditorVisibleSizeInPixels(editor, widthInPixels, heightInPixels)
    assertThat(grid.columns).isEqualTo(columns)
    assertThat(grid.rows).isEqualTo(rows)
    return grid
  }

  private fun <T> withJreHiDpiEnabled(block: () -> T): T {
    val flag = JreHiDpiUtil.test_jreHiDPI()
    val original = flag.get()
    flag.set(true)
    try {
      return block()
    }
    finally {
      flag.set(original)
    }
  }
}

/** The graphics configuration of a display that draws [scale] device pixels per user-space pixel. */
private class ScaledDisplayConfiguration(scale: Double) : GraphicsConfiguration() {
  private val transform = AffineTransform.getScaleInstance(scale, scale)
  private val image = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics().deviceConfiguration

  override fun getDevice(): GraphicsDevice = image.device
  override fun getColorModel(): ColorModel = image.colorModel
  override fun getColorModel(transparency: Int): ColorModel? = image.getColorModel(transparency)
  override fun getDefaultTransform(): AffineTransform = AffineTransform(transform)
  override fun getNormalizingTransform(): AffineTransform = AffineTransform(transform)
  override fun getBounds(): Rectangle = image.bounds
}
