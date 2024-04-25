// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

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
import java.awt.Color

@Suppress("ConstPropertyName")
internal object TerminalUi {
  const val blockTopInset = 8
  const val blockBottomInset = 12
  const val blockLeftInset = 12
  const val blockRightInset = 12
  const val cornerToBlockInset = 7
  const val commandToOutputInset = 2
  const val blockArc = 8
  const val blocksGap = 6

  const val exitCodeRightInset = 8
  const val exitCodeTextIconGap = 4

  const val promptTopInset = 11
  const val promptBottomInset = 12
  const val promptToCommandInset = 2

  const val alternateBufferLeftInset = 4

  const val searchComponentWidth = 500

  fun defaultBackground(editor: Editor? = null): JBColor {
    return createColorBoundToColorKey(BlockTerminalColors.DEFAULT_BACKGROUND, editor) {
      it.defaultBackground
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