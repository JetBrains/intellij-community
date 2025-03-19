// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.output

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer
import com.intellij.openapi.editor.markup.LineMarkerRenderer
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.terminal.BlockTerminalColors
import org.jetbrains.plugins.terminal.block.ui.*
import java.awt.Graphics
import java.awt.Rectangle

internal class EmptyHighlighterRenderer : CustomHighlighterRenderer {
  override fun paint(editor: Editor, highlighter: RangeHighlighter, g: Graphics) {
  }
}

internal class EmptyLineMarkerRenderer : LineMarkerRenderer {
  override fun paint(editor: Editor, g: Graphics, r: Rectangle) {
  }
}

internal interface BlockDecorationState {
  val backgroundRenderer: CustomHighlighterRenderer
  val cornersRenderer: CustomHighlighterRenderer
  val leftAreaRenderer: LineMarkerRenderer
}

internal class DefaultBlockDecorationState : BlockDecorationState {
  override val backgroundRenderer: CustomHighlighterRenderer = EmptyHighlighterRenderer()
  override val cornersRenderer: CustomHighlighterRenderer = BlockSeparatorRenderer()
  override val leftAreaRenderer: LineMarkerRenderer = EmptyLineMarkerRenderer()
}

internal open class SelectedBlockDecorationState : BlockDecorationState {
  override val backgroundRenderer: CustomHighlighterRenderer = TerminalBlockBackgroundRenderer(BlockTerminalColors.SELECTED_BLOCK_BACKGROUND)
  override val cornersRenderer: CustomHighlighterRenderer = TerminalBlockCornersRenderer(backgroundKey = BlockTerminalColors.SELECTED_BLOCK_BACKGROUND)
  override val leftAreaRenderer: LineMarkerRenderer = TerminalBlockLeftAreaRenderer(backgroundKey = BlockTerminalColors.SELECTED_BLOCK_BACKGROUND)
}

internal open class InactiveSelectedBlockDecorationState : BlockDecorationState {
  override val backgroundRenderer: CustomHighlighterRenderer = TerminalBlockBackgroundRenderer(BlockTerminalColors.INACTIVE_SELECTED_BLOCK_BACKGROUND)
  override val cornersRenderer: CustomHighlighterRenderer = TerminalBlockCornersRenderer(backgroundKey = BlockTerminalColors.INACTIVE_SELECTED_BLOCK_BACKGROUND)
  override val leftAreaRenderer: LineMarkerRenderer = TerminalBlockLeftAreaRenderer(backgroundKey = BlockTerminalColors.INACTIVE_SELECTED_BLOCK_BACKGROUND)
}

internal class SelectedErrorBlockDecorationState : SelectedBlockDecorationState() {
  override val leftAreaRenderer: LineMarkerRenderer
    get() = TerminalBlockLeftErrorRendererWrapper(super.leftAreaRenderer)
}

internal open class HoveredBlockDecorationState(gradientCache: GradientTextureCache) : BlockDecorationState {
  override val backgroundRenderer: CustomHighlighterRenderer = TerminalBlockBackgroundRenderer(gradientCache = gradientCache)
  override val cornersRenderer: CustomHighlighterRenderer = TerminalBlockCornersRenderer(gradientCache = gradientCache)
  override val leftAreaRenderer: LineMarkerRenderer = TerminalBlockLeftAreaRenderer(backgroundKey = BlockTerminalColors.HOVERED_BLOCK_BACKGROUND_START)
}

internal class HoveredErrorBlockDecorationState(gradientCache: GradientTextureCache) : HoveredBlockDecorationState(gradientCache) {
  override val leftAreaRenderer: LineMarkerRenderer
    get() = TerminalBlockLeftErrorRendererWrapper(super.leftAreaRenderer)
}

internal class InactiveSelectedErrorBlockDecorationState : InactiveSelectedBlockDecorationState() {
  override val leftAreaRenderer: LineMarkerRenderer
    get() = TerminalBlockLeftErrorRendererWrapper(super.leftAreaRenderer)
}

internal class ErrorBlockDecorationState : BlockDecorationState {
  override val backgroundRenderer: CustomHighlighterRenderer = EmptyHighlighterRenderer()
  override val cornersRenderer: CustomHighlighterRenderer = BlockSeparatorRenderer()
  override val leftAreaRenderer: LineMarkerRenderer = TerminalBlockLeftErrorRenderer()
}
