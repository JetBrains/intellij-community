// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.settings.ui

import com.intellij.mcpserver.McpServerBundle
import com.intellij.mcpserver.McpToolFilterProvider.McpToolState
import com.intellij.openapi.ui.ComboBox
import com.intellij.util.ui.AbstractTableCellEditor
import java.awt.Component
import javax.swing.DefaultComboBoxModel
import javax.swing.JTable

internal class McpToolStateEditor : AbstractTableCellEditor() {
  private val comboBox = ComboBox<McpToolStateItem>()
  private var currentState: McpToolState = McpToolState.ON_DEMAND

  init {
    val items = arrayOf(
      McpToolStateItem(McpToolState.ON, McpServerBundle.message("mcp.tool.state.on")),
      McpToolStateItem(McpToolState.ON_DEMAND, McpServerBundle.message("mcp.tool.state.on.demand")),
      McpToolStateItem(McpToolState.OFF, McpServerBundle.message("mcp.tool.state.off"))
    )
    comboBox.model = DefaultComboBoxModel(items)
    
    comboBox.addActionListener {
      val selectedItem = comboBox.selectedItem as? McpToolStateItem
      if (selectedItem != null) {
        currentState = selectedItem.state
        stopCellEditing()
      }
    }
  }

  override fun getTableCellEditorComponent(
    table: JTable,
    value: Any?,
    isSelected: Boolean,
    row: Int,
    column: Int
  ): Component {
    if (value is McpToolState) {
      currentState = value
      val itemIndex = when (value) {
        McpToolState.ON -> 0
        McpToolState.ON_DEMAND -> 1
        McpToolState.OFF -> 2
      }
      comboBox.selectedIndex = itemIndex
    }
    return comboBox
  }

  override fun getCellEditorValue(): McpToolState {
    return currentState
  }

  private data class McpToolStateItem(val state: McpToolState, val displayName: String) {
    override fun toString(): String = displayName
  }
}
