// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.editor.markup.CustomHighlighterRenderer
import com.intellij.openapi.editor.markup.LineMarkerRenderer
import org.jetbrains.plugins.terminal.exp.ui.GradientTextureCache
import org.jetbrains.plugins.terminal.exp.ui.TerminalBlockBackgroundRenderer
import org.jetbrains.plugins.terminal.exp.ui.TerminalBlockCornersRenderer
import org.jetbrains.plugins.terminal.exp.ui.TerminalBlockLeftAreaRenderer

internal interface BlockDecorationState {
  val backgroundRenderer: CustomHighlighterRenderer
  val cornersRenderer: CustomHighlighterRenderer
  val leftAreaRenderer: LineMarkerRenderer
}

internal class DefaultBlockDecorationState(gradientCache: GradientTextureCache) : BlockDecorationState {
  override val backgroundRenderer: CustomHighlighterRenderer = TerminalBlockBackgroundRenderer(gradientCache)
  override val cornersRenderer: CustomHighlighterRenderer = TerminalBlockCornersRenderer(gradientCache)
  override val leftAreaRenderer: LineMarkerRenderer = TerminalBlockLeftAreaRenderer(gradientCache.colorStart)
}

internal class SelectedBlockDecorationState : BlockDecorationState {
  override val backgroundRenderer: CustomHighlighterRenderer = TerminalBlockBackgroundRenderer(TerminalUi.selectedBlockBackground)
  override val cornersRenderer: CustomHighlighterRenderer = TerminalBlockCornersRenderer(background = TerminalUi.selectedBlockBackground,
                                                                                         strokeBackground = TerminalUi.selectedBlockStrokeColor,
                                                                                         strokeWidth = 2)
  override val leftAreaRenderer: LineMarkerRenderer = TerminalBlockLeftAreaRenderer(background = TerminalUi.selectedBlockBackground,
                                                                                    strokeBackground = TerminalUi.selectedBlockStrokeColor,
                                                                                    strokeWidth = 2)
}

internal class InactiveSelectedBlockDecorationState : BlockDecorationState {
  override val backgroundRenderer: CustomHighlighterRenderer = TerminalBlockBackgroundRenderer(TerminalUi.inactiveSelectedBlockBackground)
  override val cornersRenderer: CustomHighlighterRenderer = TerminalBlockCornersRenderer(
    background = TerminalUi.inactiveSelectedBlockBackground,
    strokeBackground = TerminalUi.inactiveSelectedBlockStrokeColor,
    strokeWidth = 2
  )
  override val leftAreaRenderer: LineMarkerRenderer = TerminalBlockLeftAreaRenderer(
    background = TerminalUi.inactiveSelectedBlockBackground,
    strokeBackground = TerminalUi.inactiveSelectedBlockStrokeColor,
    strokeWidth = 2
  )
}

internal class ErrorBlockDecorationState : BlockDecorationState {
  override val backgroundRenderer: CustomHighlighterRenderer = TerminalBlockBackgroundRenderer(TerminalUi.errorBlockBackground)
  override val cornersRenderer: CustomHighlighterRenderer = TerminalBlockCornersRenderer(background = TerminalUi.errorBlockBackground,
                                                                                         strokeBackground = TerminalUi.errorBlockStrokeColor,
                                                                                         strokeWidth = 1)
  override val leftAreaRenderer: LineMarkerRenderer = TerminalBlockLeftAreaRenderer(background = TerminalUi.errorBlockBackground,
                                                                                    strokeBackground = TerminalUi.errorBlockStrokeColor,
                                                                                    strokeWidth = 1)
}