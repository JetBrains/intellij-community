// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.ui

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.terminal.BlockTerminalColors
import com.intellij.terminal.TerminalColorPalette
import com.jediterm.core.Color
import com.jediterm.terminal.ui.AwtTransformers
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class BlockTerminalColorPalette : TerminalColorPalette() {
  private val colorKeys = BlockTerminalColors.KEYS

  private val colorsScheme: EditorColorsScheme
    get() = EditorColorsManager.getInstance().globalScheme

  override val defaultForeground: Color
    get() {
      val foregroundColor = colorsScheme.getColor(BlockTerminalColors.DEFAULT_FOREGROUND)
      return AwtTransformers.fromAwtColor(foregroundColor ?: colorsScheme.defaultForeground)!!
    }
  override val defaultBackground: Color
    get() {
      return AwtTransformers.fromAwtColor(TerminalUi.defaultBackground())!!
    }

  override fun getAttributesByColorIndex(index: Int): TextAttributes? {
    val key = getAnsiColorKey(index) ?: return null
    return colorsScheme.getAttributes(key)
  }

  private fun getAnsiColorKey(value: Int): TextAttributesKey? {
    return if (value in colorKeys.indices) {
      colorKeys[value]
    }
    else null
  }
}
