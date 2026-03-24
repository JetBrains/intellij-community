// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.settings.ui

import com.intellij.mcpserver.McpServerBundle
import com.intellij.mcpserver.McpToolFilterProvider.McpToolState
import com.intellij.ui.components.JBLabel
import java.awt.Component
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.table.TableCellRenderer

internal class McpToolStateRenderer : TableCellRenderer {
  private val label = JBLabel()
  private val emptyPanel = JPanel()

  override fun getTableCellRendererComponent(
    table: JTable,
    value: Any?,
    isSelected: Boolean,
    hasFocus: Boolean,
    row: Int,
    column: Int
  ): Component {
    if (value is McpToolState) {
      label.text = when (value) {
        McpToolState.ON -> McpServerBundle.message("mcp.tool.state.on")
        McpToolState.OFF -> McpServerBundle.message("mcp.tool.state.off")
        McpToolState.ON_DEMAND -> McpServerBundle.message("mcp.tool.state.on.demand")
      }
      label.background = if (isSelected) table.selectionBackground else table.background
      label.foreground = if (isSelected) table.selectionForeground else table.foreground
      return label
    }
    
    emptyPanel.background = if (isSelected) table.selectionBackground else table.background
    return emptyPanel
  }
}
