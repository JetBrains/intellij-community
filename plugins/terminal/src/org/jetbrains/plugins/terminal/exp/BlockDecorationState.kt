// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.editor.markup.CustomHighlighterRenderer
import com.intellij.openapi.editor.markup.LineMarkerRenderer
import org.jetbrains.plugins.terminal.exp.ui.TerminalBlockBackgroundRenderer
import org.jetbrains.plugins.terminal.exp.ui.TerminalBlockCornersRenderer
import org.jetbrains.plugins.terminal.exp.ui.TerminalBlockLeftAreaRenderer

interface BlockDecorationState {
  val name: String
  val priority: Int
  val backgroundRenderer: CustomHighlighterRenderer
  val cornersRenderer: CustomHighlighterRenderer
  val leftAreaRenderer: LineMarkerRenderer
}

abstract class AbstractBlockDecorationState(override val name: String, override val priority: Int) : BlockDecorationState

class DefaultBlockDecorationState : AbstractBlockDecorationState(NAME, priority = 0) {
  override val backgroundRenderer: CustomHighlighterRenderer = TerminalBlockBackgroundRenderer(TerminalUi.blockBackgroundStart,
                                                                                               TerminalUi.blockBackgroundEnd)
  override val cornersRenderer: CustomHighlighterRenderer = TerminalBlockCornersRenderer(TerminalUi.blockBackgroundStart,
                                                                                         TerminalUi.blockBackgroundEnd)
  override val leftAreaRenderer: LineMarkerRenderer = TerminalBlockLeftAreaRenderer(TerminalUi.blockBackgroundStart)

  companion object {
    const val NAME: String = "DEFAULT"
  }
}

class SelectedBlockDecorationState : AbstractBlockDecorationState(NAME, priority = 2) {
  override val backgroundRenderer: CustomHighlighterRenderer = TerminalBlockBackgroundRenderer(TerminalUi.selectedBlockBackground)
  override val cornersRenderer: CustomHighlighterRenderer = TerminalBlockCornersRenderer(TerminalUi.selectedBlockBackground,
                                                                                         backgroundEnd = null,
                                                                                         TerminalUi.selectedBlockStrokeColor,
                                                                                         strokeWidth = 2)
  override val leftAreaRenderer: LineMarkerRenderer = TerminalBlockLeftAreaRenderer(TerminalUi.selectedBlockBackground,
                                                                                    TerminalUi.selectedBlockStrokeColor,
                                                                                    strokeWidth = 2)

  companion object {
    const val NAME: String = "SELECTED"
  }
}

class ErrorBlockDecorationState : AbstractBlockDecorationState(NAME, priority = 1) {
  override val backgroundRenderer: CustomHighlighterRenderer = TerminalBlockBackgroundRenderer(TerminalUi.errorBlockBackground)
  override val cornersRenderer: CustomHighlighterRenderer = TerminalBlockCornersRenderer(TerminalUi.errorBlockBackground,
                                                                                         backgroundEnd = null,
                                                                                         TerminalUi.errorBlockStrokeColor,
                                                                                         strokeWidth = 1)
  override val leftAreaRenderer: LineMarkerRenderer = TerminalBlockLeftAreaRenderer(TerminalUi.errorBlockBackground,
                                                                                    TerminalUi.errorBlockStrokeColor,
                                                                                    strokeWidth = 1)

  companion object {
    const val NAME: String = "ERROR"
  }
}