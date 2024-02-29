// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.editor.markup.CustomHighlighterRenderer
import com.intellij.openapi.editor.markup.LineMarkerRenderer
import com.intellij.terminal.BlockTerminalColors
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
  override val leftAreaRenderer: LineMarkerRenderer = TerminalBlockLeftAreaRenderer(backgroundKey = gradientCache.colorStartKey,
                                                                                    failoverBackgroundKey = gradientCache.colorEndKey)
}

internal class SelectedBlockDecorationState : BlockDecorationState {
  override val backgroundRenderer: CustomHighlighterRenderer = TerminalBlockBackgroundRenderer(BlockTerminalColors.SELECTED_BLOCK_BACKGROUND)
  override val cornersRenderer: CustomHighlighterRenderer = TerminalBlockCornersRenderer(backgroundKey = BlockTerminalColors.SELECTED_BLOCK_BACKGROUND,
                                                                                         strokeBackgroundKey = BlockTerminalColors.SELECTED_BLOCK_STROKE_COLOR,
                                                                                         strokeWidth = 2)
  override val leftAreaRenderer: LineMarkerRenderer = TerminalBlockLeftAreaRenderer(backgroundKey = BlockTerminalColors.SELECTED_BLOCK_BACKGROUND,
                                                                                    strokeBackgroundKey = BlockTerminalColors.SELECTED_BLOCK_STROKE_COLOR,
                                                                                    strokeWidth = 2)
}

internal class InactiveSelectedBlockDecorationState : BlockDecorationState {
  override val backgroundRenderer: CustomHighlighterRenderer = TerminalBlockBackgroundRenderer(BlockTerminalColors.INACTIVE_SELECTED_BLOCK_BACKGROUND)
  override val cornersRenderer: CustomHighlighterRenderer = TerminalBlockCornersRenderer(
    backgroundKey = BlockTerminalColors.INACTIVE_SELECTED_BLOCK_BACKGROUND,
    strokeBackgroundKey = BlockTerminalColors.INACTIVE_SELECTED_BLOCK_STROKE_COLOR,
    strokeWidth = 2
  )
  override val leftAreaRenderer: LineMarkerRenderer = TerminalBlockLeftAreaRenderer(
    backgroundKey = BlockTerminalColors.INACTIVE_SELECTED_BLOCK_BACKGROUND,
    strokeBackgroundKey = BlockTerminalColors.INACTIVE_SELECTED_BLOCK_STROKE_COLOR,
    strokeWidth = 2
  )
}

internal class ErrorBlockDecorationState(gradientCache: GradientTextureCache) : BlockDecorationState {
  override val backgroundRenderer: CustomHighlighterRenderer = TerminalBlockBackgroundRenderer(gradientCache)
  override val cornersRenderer: CustomHighlighterRenderer = TerminalBlockCornersRenderer(gradientCache = gradientCache,
                                                                                         strokeBackgroundKey = BlockTerminalColors.ERROR_BLOCK_STROKE_COLOR,
                                                                                         strokeWidth = 1)
  override val leftAreaRenderer: LineMarkerRenderer = TerminalBlockLeftAreaRenderer(backgroundKey = gradientCache.colorStartKey,
                                                                                    strokeBackgroundKey = BlockTerminalColors.ERROR_BLOCK_STROKE_COLOR,
                                                                                    strokeWidth = 1)
}