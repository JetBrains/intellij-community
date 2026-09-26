// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.output.highlighting

import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface TerminalCommandBlockHighlighterProvider {
  companion object {
    internal val COMMAND_BLOCK_HIGHLIGHTER_PROVIDER_EP_NAME: ExtensionPointName<TerminalCommandBlockHighlighterProvider> = ExtensionPointName.create(
      "org.jetbrains.plugins.terminal.exp.commandBlockHighlighterProvider"
    )
  }

  fun getHighlighter(colorsScheme: EditorColorsScheme): TerminalCommandBlockHighlighter
}
