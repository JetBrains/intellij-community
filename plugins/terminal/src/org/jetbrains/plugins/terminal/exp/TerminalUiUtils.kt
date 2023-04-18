// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.Project
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.JBUI
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.image.BufferedImage
import javax.swing.JScrollPane
import kotlin.math.ceil

object TerminalUiUtils {
  fun createEditor(document: Document, project: Project, settings: JBTerminalSystemSettingsProviderBase): EditorImpl {
    val editor = EditorFactory.getInstance().createEditor(document, project, EditorKind.CONSOLE) as EditorImpl
    editor.isScrollToCaret = false
    editor.setCustomCursor(this, Cursor.getDefaultCursor())
    editor.scrollPane.border = JBUI.Borders.empty()
    editor.scrollPane.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
    editor.gutterComponentEx.isPaintBackground = false

    editor.colorsScheme.apply {
      editorFontName = settings.terminalFont.fontName
      editorFontSize = settings.terminalFont.size
      lineSpacing = settings.lineSpacing
    }

    editor.settings.apply {
      isShowingSpecialChars = false
      isLineNumbersShown = false
      setGutterIconsShown(false)
      isRightMarginShown = false
      isFoldingOutlineShown = false
      isCaretRowShown = false
      additionalLinesCount = 0
      additionalColumnsCount = 0
      isBlockCursor = true
    }
    return editor
  }

  fun calculateCharSize(font: Font, lineSpacing: Float): Dimension {
    val img: BufferedImage = ImageUtil.createImage(1, 1, BufferedImage.TYPE_INT_ARGB)
    val graphics = img.createGraphics().also { it.font = font }
    try {
      val metrics = graphics.fontMetrics
      val width = metrics.charWidth('W')
      val metricsHeight = metrics.height
      val height = ceil(metricsHeight * lineSpacing).toInt()
      return Dimension(width, height)
    }
    finally {
      img.flush()
      graphics.dispose()
    }
  }
}