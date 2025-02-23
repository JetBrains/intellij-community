// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.terminal.BlockTerminalColors
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.Color

@Suppress("ConstPropertyName")
@ApiStatus.Internal
object TerminalUi {
  const val blockTopInset: Int = 6
  const val blockBottomInset: Int = 6
  const val blockLeftInset: Int = 9
  const val blockRightInset: Int = 12
  const val cornerToBlockInset: Int = 10
  const val cornerToBlockOffset: Int = 7
  const val commandToOutputInset: Int = 2
  const val blockArc: Int = 8
  const val blocksGap: Int = 0
  const val blockSeparatorRightOffset: Int = 19
  const val blockSelectionSeparatorGap: Int = 1

  const val errorLineYOffset: Int = 2
  const val errorLineRightOffset: Int = 9
  const val errorLineWidth: Int = 3
  const val errorLineArc: Int = 4

  const val exitCodeRightInset: Int = 8
  const val exitCodeTextIconGap: Int = 4

  const val promptTopInset: Int = 6
  const val promptBottomInset: Int = 12
  const val promptToCommandInset: Int = 2

  const val alternateBufferLeftInset: Int = 4

  const val searchComponentWidth: Int = 500

  const val blockTopInlayPriority: Int = 300
  const val terminalTopInlayPriority: Int = 200
  const val blockBottomInlayPriority: Int = 100
  const val terminalBottomInlayPriority: Int = 0

  fun defaultBackground(editor: Editor? = null): JBColor {
    return JBColor.lazy {
      val colorsScheme = editor?.colorsScheme ?: EditorColorsManager.getInstance().globalScheme
      colorsScheme.defaultBackground
    }
  }

  fun defaultForeground(editor: Editor? = null): JBColor {
    return createColorBoundToColorKey(BlockTerminalColors.DEFAULT_FOREGROUND, editor) {
      it.defaultForeground
    }
  }

  private fun createColorBoundToColorKey(colorKey: ColorKey, editor: Editor? = null, default: (EditorColorsScheme) -> Color): JBColor {
    return JBColor.lazy {
      val colorsScheme = editor?.colorsScheme ?: EditorColorsManager.getInstance().globalScheme
      colorsScheme.getColor(colorKey) ?: default(colorsScheme)
    }
  }

  /**
   * Unfortunately, `editor.backgroundColor = terminalDefaultBackground(editor)` doesn't work when
   * colors scheme is changed. Because [com.intellij.openapi.editor.impl.EditorImpl.setBackgroundColor]
   * doesn't save a passed in color if the color is equal to the default one.
   */
  fun EditorEx.useTerminalDefaultBackground(parentDisposable: Disposable) {
    backgroundColor = defaultBackground(this)
    ApplicationManager.getApplication().messageBus.connect(parentDisposable).subscribe(EditorColorsManager.TOPIC, EditorColorsListener {
      backgroundColor = defaultBackground(this)
    })
  }

  fun promptSeparatorColor(editor: Editor): Color {
    return createColorBoundToColorKey(BlockTerminalColors.PROMPT_SEPARATOR_COLOR, editor) {
      JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()
    }
  }
}
