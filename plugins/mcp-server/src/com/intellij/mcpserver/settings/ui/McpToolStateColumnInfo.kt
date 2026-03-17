// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.settings.ui

import com.intellij.mcpserver.McpServerBundle
import com.intellij.mcpserver.McpToolFilterProvider.McpToolState
import com.intellij.util.ui.ColumnInfo
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer
import javax.swing.tree.DefaultMutableTreeNode

internal class McpToolStateColumnInfo : ColumnInfo<DefaultMutableTreeNode, McpToolState>(
  McpServerBundle.message("mcp.tool.state.column")
) {
  private val renderer = McpToolStateRenderer()
  private val editor = McpToolStateEditor()

  override fun valueOf(node: DefaultMutableTreeNode): McpToolState? {
    return when (val userObject = node.userObject) {
      is McpToolNode -> userObject.state
      is CategoryNode -> {
        val childNodes = (0 until node.childCount).mapNotNull { i ->
          val child = node.getChildAt(i) as? DefaultMutableTreeNode
          child?.userObject as? McpToolNode
        }
        userObject.getCommonState(childNodes)
      }
      else -> null
    }
  }

  override fun isCellEditable(node: DefaultMutableTreeNode): Boolean {
    return node.userObject is McpToolNode || node.userObject is CategoryNode
  }

  override fun setValue(node: DefaultMutableTreeNode, value: McpToolState?) {
    if (value == null) return
    
    when (node.userObject) {
      is McpToolNode -> {
        (node.userObject as McpToolNode).state = value
      }
      is CategoryNode -> {
        // Set state for all children
        for (i in 0 until node.childCount) {
          val child = node.getChildAt(i) as? DefaultMutableTreeNode ?: continue
          val toolNode = child.userObject as? McpToolNode ?: continue
          toolNode.state = value
        }
      }
    }
  }

  override fun getRenderer(item: DefaultMutableTreeNode?): TableCellRenderer {
    return renderer
  }

  override fun getEditor(item: DefaultMutableTreeNode?): TableCellEditor {
    return editor
  }
}

internal class McpToolNode(
  val toolName: String,
  val toolDescription: String,
  var state: McpToolState
) {
  override fun toString(): String = toolName
}

internal class CategoryNode(val categoryName: String) {
  override fun toString(): String = categoryName
  
  fun getCommonState(childNodes: List<McpToolNode>): McpToolState? {
    if (childNodes.isEmpty()) return null
    val firstState = childNodes.first().state
    return if (childNodes.all { it.state == firstState }) firstState else null
  }
}
