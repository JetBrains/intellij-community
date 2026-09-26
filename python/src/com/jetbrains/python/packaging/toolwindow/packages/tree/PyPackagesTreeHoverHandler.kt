// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.packages.tree

import com.jetbrains.python.packaging.toolwindow.packages.tree.renderers.PyPackageTreeCellRenderer
import java.awt.Cursor
import java.awt.event.MouseEvent
import javax.swing.tree.DefaultMutableTreeNode

/**
 * Tracks hover state (link row, trailing-icon row + cursor) for [PyPackagesTree] and repaints affected rows.
 */
internal class PyPackagesTreeHoverHandler(private val tree: PyPackagesTree) {

  var linkHoveredRow: Int = -1
    private set
  var iconHoveredRow: Int = -1
    private set
  var changeIconHoveredRow: Int = -1
    private set

  fun handleMouseMoved(e: MouseEvent) {
    val row = tree.getClosestRowForLocation(e.x, e.y).takeIf { it >= 0 } ?: run { clearLinkHover(); return }
    val rowBounds = tree.getRowBounds(row) ?: run { clearLinkHover(); return }
    if (e.y < rowBounds.y || e.y >= rowBounds.y + rowBounds.height) { clearLinkHover(); return }
    if (tree.isReadOnly) { clearLinkHover(); return }

    val node = tree.getPathForRow(row)?.lastPathComponent as DefaultMutableTreeNode? ?: run { clearLinkHover(); return }
    val renderer = tree.cellRenderer.getTreeCellRendererComponent(tree, node, true, false, true, row, false) as PyPackageTreeCellRenderer
    val relativeX = e.x - rowBounds.x

    val lStart = renderer.linkStartX
    val lEnd = renderer.linkEndX
    if (lStart in 1..<lEnd && relativeX in lStart..lEnd) {
      tree.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
      setLinkHover(row)
      clearIconHover()
      clearChangeIconHover()
      return
    }

    val changeIconX = renderer.inlineChangeVersionIconX
    val changeIcon = renderer.inlineChangeVersionIcon
    if (changeIconX > 0 && changeIcon != null && relativeX in changeIconX..(changeIconX + changeIcon.iconWidth)) {
      tree.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
      clearLinkHoverRowOnly()
      clearIconHover()
      setChangeIconHover(row)
      return
    }

    val trailingIconX = renderer.trailingIconX
    val trailingIcon = renderer.trailingIcon
    if (trailingIconX > 0 && trailingIcon != null && relativeX in trailingIconX..(trailingIconX + trailingIcon.iconWidth)) {
      tree.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
      clearLinkHoverRowOnly()
      clearChangeIconHover()
      setIconHover(row)
      return
    }

    clearLinkHover()
  }

  fun clearLinkHover() {
    clearLinkHoverRowOnly()
    clearIconHover()
    clearChangeIconHover()
    tree.cursor = Cursor.getDefaultCursor()
  }

  private fun setChangeIconHover(row: Int) {
    if (changeIconHoveredRow != row) {
      val prev = changeIconHoveredRow
      changeIconHoveredRow = row
      if (prev >= 0) repaintFullRow(prev)
      repaintFullRow(row)
    }
  }

  private fun clearChangeIconHover() {
    if (changeIconHoveredRow != -1) {
      val prev = changeIconHoveredRow
      changeIconHoveredRow = -1
      repaintFullRow(prev)
    }
  }

  private fun setLinkHover(row: Int) {
    if (linkHoveredRow != row) {
      val prev = linkHoveredRow
      linkHoveredRow = row
      if (prev >= 0) repaintFullRow(prev)
      repaintFullRow(row)
    }
  }

  private fun clearLinkHoverRowOnly() {
    if (linkHoveredRow != -1) {
      val prev = linkHoveredRow
      linkHoveredRow = -1
      repaintFullRow(prev)
    }
  }

  private fun setIconHover(row: Int) {
    if (iconHoveredRow != row) {
      val prev = iconHoveredRow
      iconHoveredRow = row
      if (prev >= 0) repaintFullRow(prev)
      repaintFullRow(row)
    }
  }

  private fun clearIconHover() {
    if (iconHoveredRow != -1) {
      val prev = iconHoveredRow
      iconHoveredRow = -1
      repaintFullRow(prev)
    }
  }

  private fun repaintFullRow(row: Int) {
    val b = tree.getRowBounds(row) ?: return
    tree.repaint(0, b.y, tree.width, b.height)
  }
}
