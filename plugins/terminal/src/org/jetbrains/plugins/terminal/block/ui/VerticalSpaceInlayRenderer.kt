// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.ui

import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.util.ui.JBUI

/**
 * Inlay renderer with an empty width and the height specified by [heightSupplier].
 * Can be used as a renderer of the block inlay to create an empty space between lines.
 *
 * @param heightSupplier should return unscaled height if the inlay.
 */
internal class VerticalSpaceInlayRenderer(private val heightSupplier: () -> Int) : EditorCustomElementRenderer {
  /**
   * @param height unscaled height of the inlay.
   */
  constructor(height: Int) : this({ height })

  override fun calcWidthInPixels(inlay: Inlay<*>): Int = 0

  override fun calcHeightInPixels(inlay: Inlay<*>): Int = JBUI.scale(heightSupplier())
}