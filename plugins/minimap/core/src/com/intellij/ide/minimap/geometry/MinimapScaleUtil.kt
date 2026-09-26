// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.geometry

import com.intellij.ide.minimap.layout.MinimapLayoutPolicy
import com.intellij.ide.minimap.layout.MinimapLayoutUtil
import com.intellij.ide.minimap.settings.MinimapScaleMode
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.util.ui.JBUI

object MinimapScaleUtil {
  private val MIN_FIT_WIDTH: Int = JBUI.scale(60)

  fun contentHeight(editor: Editor, projectedLineCount: Int = editor.document.lineCount): Int {
    return documentHeight(editor, projectedLineCount)
  }

  fun computeScale(editor: Editor,
                   panelHeight: Int,
                   fixedWidth: Int,
                   scaleMode: MinimapScaleMode): MinimapScaleData {
    val additionalWidth = MinimapLayoutPolicy.forLayoutProfile(editor).additionalPanelWidthPx
    val maxWidth = fixedWidth.coerceAtLeast(1)
    if (scaleMode != MinimapScaleMode.FIT) {
      return MinimapScaleData(width = applyAdditionalWidth(maxWidth, additionalWidth), fitToHeight = false)
    }

    val documentHeight = documentHeight(editor, editor.document.lineCount)
    if (documentHeight <= 0 || panelHeight <= 0) {
      return MinimapScaleData(width = applyAdditionalWidth(maxWidth, additionalWidth), fitToHeight = false)
    }

    val logicalWidth = logicalWidth(editor)
    if (logicalWidth <= 0) {
      return MinimapScaleData(width = applyAdditionalWidth(maxWidth, additionalWidth), fitToHeight = false)
    }

    val idealWidth = panelHeight.toDouble() * logicalWidth / documentHeight.toDouble()
    val minWidth = MIN_FIT_WIDTH.coerceAtMost(maxWidth).coerceAtLeast(1)
    val width = idealWidth.toInt().coerceIn(minWidth, maxWidth)
    val fitToHeight = idealWidth <= maxWidth.toDouble()
    return MinimapScaleData(width = applyAdditionalWidth(width, additionalWidth), fitToHeight = fitToHeight)
  }

  fun effectiveWidth(editor: Editor, panelHeight: Int, fixedWidth: Int, scaleMode: MinimapScaleMode): Int {
    return computeScale(editor, panelHeight, fixedWidth, scaleMode).width
  }

  private fun documentHeight(editor: Editor, lineCount: Int): Int {
    if (lineCount <= 0) return 0
    val height = lineCount.toLong() * editor.lineHeight.toLong()
    return height.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
  }

  private fun logicalWidth(editor: Editor): Int {
    val rightMargin = MinimapLayoutUtil.getRightMarginChars(editor)
    val charWidth = EditorUtil.getPlainSpaceWidth(editor)
    val visibleWidth = editor.scrollingModel.visibleArea.width
    return MinimapLayoutUtil.computeLogicalWidth(rightMargin, charWidth, visibleWidth)
  }

  private fun applyAdditionalWidth(width: Int, additionalWidth: Int): Int {
    if (additionalWidth <= 0) return width
    return width.coerceAtMost(Int.MAX_VALUE - additionalWidth) + additionalWidth
  }
}
