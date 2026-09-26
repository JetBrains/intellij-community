// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap

import com.intellij.ui.components.JBScrollPane
import java.awt.Component
import java.awt.Container
import java.awt.Rectangle
import javax.swing.JScrollPane
import javax.swing.border.Border

/**
 * A scroll pane layout for the minimap "insideScrollbar" mode that positions the minimap
 * between the editor viewport and the vertical scrollbar.
 *
 * The scrollbar is never moved: it stays in its natural location, either inside the scroll pane
 * or on the layered pane when sticky lines are enabled. The viewport strip for the minimap is
 * reserved via [createViewportBorder], so [JBScrollPane.Layout] makes scrollbar decisions using
 * the reduced editor width.
 */
internal class MinimapScrollPaneLayout(private val minimap: Component) : JBScrollPane.Layout() {
  override fun layoutContainer(parent: Container) {
    super.layoutContainer(parent)

    val scrollPane = parent as? JScrollPane ?: return
    val viewport = scrollPane.viewport ?: return
    if (!minimap.isVisible) {
      minimap.setBounds(0, 0, 0, 0)
      return
    }

    val minimapWidth = minimap.preferredSize.width.coerceAtLeast(0)
    if (minimapWidth == 0 || viewport.height <= 0) {
      minimap.setBounds(0, 0, 0, 0)
      return
    }

    val vsbOnLeft = isMinimapVerticalScrollBarOnLeft(scrollPane)
    val minimapBounds = computeMinimapBounds(scrollPane, viewport.bounds, minimapWidth, vsbOnLeft)
    minimap.bounds = minimapBounds
    trimHorizontalScrollbar(scrollPane, minimapBounds, vsbOnLeft)
    updateZOrder(scrollPane)
  }

  private fun computeMinimapBounds(
    scrollPane: JScrollPane,
    viewportBounds: Rectangle,
    minimapWidth: Int,
    vsbOnLeft: Boolean,
  ): Rectangle {
    val vsb = scrollPane.verticalScrollBar
    if (vsb != null && vsb.isVisible && vsb.width > 0) {
      return if (vsbOnLeft) {
        val x = vsb.x + vsb.width
        val width = (viewportBounds.x - x).coerceIn(0, minimapWidth)
        Rectangle(x, viewportBounds.y, width, viewportBounds.height)
      }
      else {
        val x = viewportBounds.x + viewportBounds.width
        val width = (vsb.x - x).coerceIn(0, minimapWidth)
        Rectangle(x, viewportBounds.y, width, viewportBounds.height)
      }
    }

    return if (vsbOnLeft) {
      val width = minimapWidth.coerceAtMost(viewportBounds.x)
      Rectangle(viewportBounds.x - width, viewportBounds.y, width, viewportBounds.height)
    }
    else {
      val x = viewportBounds.x + viewportBounds.width
      val width = minimapWidth.coerceAtMost((scrollPane.width - x).coerceAtLeast(0))
      Rectangle(x, viewportBounds.y, width, viewportBounds.height)
    }
  }

  private fun trimHorizontalScrollbar(scrollPane: JScrollPane, minimapBounds: Rectangle, vsbOnLeft: Boolean) {
    val hsb = scrollPane.horizontalScrollBar
    if (hsb == null || !hsb.isVisible || minimapBounds.width <= 0) return

    val hsbBounds = hsb.bounds
    if (vsbOnLeft) {
      val right = hsbBounds.x + hsbBounds.width
      val x = minimapBounds.x + minimapBounds.width
      hsb.setBounds(x, hsbBounds.y, (right - x).coerceAtLeast(0), hsbBounds.height)
    }
    else {
      hsb.setBounds(hsbBounds.x, hsbBounds.y, (minimapBounds.x - hsbBounds.x).coerceAtLeast(0), hsbBounds.height)
    }
  }

  private fun updateZOrder(scrollPane: JScrollPane) {
    if (minimap.parent !== scrollPane) return

    synchronized(scrollPane.treeLock) {
      val vsb = scrollPane.verticalScrollBar
      if (vsb != null && vsb.parent === scrollPane && vsb.isVisible) {
        scrollPane.setComponentZOrder(vsb, 0)
        scrollPane.setComponentZOrder(minimap, 1.coerceAtMost(scrollPane.componentCount - 1))
      }
      else {
        scrollPane.setComponentZOrder(minimap, 0)
      }
    }
  }

  companion object {
    fun createViewportBorder(scrollPane: JScrollPane, minimap: Component, originalBorder: Border?): Border {
      return MinimapViewportBorder(scrollPane, minimap, originalBorder)
    }
  }
}
