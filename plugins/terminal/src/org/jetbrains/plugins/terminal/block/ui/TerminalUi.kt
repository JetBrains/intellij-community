// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.ui

import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
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

  fun defaultBackgroundLazy(): JBColor {
    return JBColor.lazy { defaultBackground() }
  }

  fun defaultBackground(colorsScheme: EditorColorsScheme? = null): Color {
    val scheme = colorsScheme ?: EditorColorsManager.getInstance().globalScheme
    return scheme.getColor(BlockTerminalColors.DEFAULT_BACKGROUND)
           ?: scheme.getColor(ConsoleViewContentType.CONSOLE_BACKGROUND_KEY)
           ?: scheme.defaultBackground
  }

  fun defaultForeground(colorsScheme: EditorColorsScheme? = null): Color {
    val scheme = colorsScheme ?: EditorColorsManager.getInstance().globalScheme
    return scheme.getColor(BlockTerminalColors.DEFAULT_FOREGROUND)
           ?: scheme.getAttributes(ConsoleViewContentType.NORMAL_OUTPUT_KEY)?.foregroundColor
           ?: scheme.defaultForeground
  }

  private fun createColorBoundToColorKey(colorKey: ColorKey, editor: Editor? = null, default: (EditorColorsScheme) -> Color): JBColor {
    return JBColor.lazy {
      val colorsScheme = editor?.colorsScheme ?: EditorColorsManager.getInstance().globalScheme
      colorsScheme.getColor(colorKey) ?: default(colorsScheme)
    }
  }

  fun promptSeparatorColor(editor: Editor? = null): Color {
    return createColorBoundToColorKey(BlockTerminalColors.PROMPT_SEPARATOR_COLOR, editor) {
      JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()
    }
  }
}
