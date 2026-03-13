// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.settings

import com.intellij.mcpserver.McpServerBundle
import com.intellij.mcpserver.McpTool
import com.intellij.mcpserver.McpToolCategory
import com.intellij.mcpserver.McpToolsMarkdownExporter
import com.intellij.mcpserver.impl.DisallowListBasedMcpToolFilterProvider
import com.intellij.mcpserver.impl.McpServerService
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.util.NlsSafe
import kotlin.io.path.writeText
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.CheckboxTree
import com.intellij.ui.CheckboxTreeTable
import com.intellij.ui.CheckedTreeNode
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.rows
import com.intellij.ui.treeStructure.treetable.TreeColumnInfo
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.tree.TreeUtil
import org.jetbrains.annotations.NonNls
import java.awt.BorderLayout
import java.awt.Dimension
import java.nio.file.Path
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode

/**
 * Configurable for managing the MCP tool disallow list.
 * Displays available tools in a tree structure (grouped by category) with checkboxes
 * to allow/disallow individual tools.
 */
class McpToolFilterConfigurable : SearchableConfigurable {
  private var mainPanel: JPanel? = null
  private var treeTable: CheckboxTreeTable? = null
  private var treeTableScrollPane: javax.swing.JScrollPane? = null
  private val toolNodes = mutableMapOf<String, CheckedTreeNode>()
  private var initialDisallowedTools: Set<String> = emptySet()
  private var toolsFilterTextArea: JBTextArea? = null
  private var initialToolsFilter: @NlsSafe String = ""

  override fun getDisplayName(): String = McpServerBundle.message("configurable.mcp.tool.filter")

  override fun getId(): @NonNls String = "com.intellij.mcpserver.settings.filter"

  override fun createComponent(): JComponent {
    val panel = JPanel(BorderLayout())

    // Get tools filtered by all providers except DisallowListBasedMcpToolFilterProvider
    val tools = McpServerService.getInstance().getMcpToolsFiltered(
      excludeProviders = setOf(DisallowListBasedMcpToolFilterProvider::class.java)
    )

    val settings = McpToolDisallowListSettings.getInstance()
    initialDisallowedTools = settings.disallowedToolNames

    val treeTableComponent = createTreeTable(tools, initialDisallowedTools)
    val scrollPane = ScrollPaneFactory.createScrollPane(treeTableComponent)
    scrollPane.preferredSize = Dimension(600, 400)

    panel.add(scrollPane, BorderLayout.CENTER)
    treeTableScrollPane = scrollPane

    // Add advanced filter UI if registry key is enabled
    if (Registry.`is`("mcp.server.show.advanced.filter.options.ui", false)) {
      val filterSettings = McpToolFilterSettings.getInstance()
      initialToolsFilter = filterSettings.toolsFilter

      val advancedPanel = panel {
        row {
          button(McpServerBundle.message("dialog.mcp.tools.export.button")) {
            exportToMarkdown()
          }
        }
        row(McpServerBundle.message("configurable.mcp.tool.filter.label")) {
          val textArea = JBTextArea(initialToolsFilter)
          textArea.lineWrap = true
          textArea.wrapStyleWord = true
          toolsFilterTextArea = textArea
          scrollCell(textArea)
            .rows(3)
            .align(AlignX.FILL)
        }
        row {
          comment(McpServerBundle.message("configurable.mcp.tool.filter.example"))
        }
      }
      panel.add(advancedPanel, BorderLayout.SOUTH)
    }

    mainPanel = panel
    treeTable = treeTableComponent
    return panel
  }

  private fun exportToMarkdown() {
    val descriptor = FileSaverDescriptor(
      McpServerBundle.message("dialog.mcp.tools.export.title"),
      McpServerBundle.message("dialog.mcp.tools.export.description"),
      "md"
    )
    val saveDialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, null)
    val fileWrapper = saveDialog.save(null as Path?, "mcp_tools.md") ?: return

    val markdown = McpToolsMarkdownExporter.generateMarkdownForAllTools()
    val virtualFile = fileWrapper.getVirtualFile(true) ?: return
    virtualFile.toNioPath().writeText(markdown)
  }

  private fun createTreeTable(tools: List<McpTool>, disallowedToolNames: Set<String>): CheckboxTreeTable {
    val root = CheckedTreeNode("Root")
    toolNodes.clear()

    val toolsByCategory = tools
      .groupBy { it.descriptor.category }
      .toSortedMap(compareBy(String.CASE_INSENSITIVE_ORDER) { it.shortName })
      .mapValues { (_, categoryTools) -> categoryTools.sortedBy { it.descriptor.name.lowercase() } }

    for ((category, categoryTools) in toolsByCategory) {
      val categoryNode = CheckedTreeNode(CategoryNode(category))
      root.add(categoryNode)

      var allToolsAllowed = true

      for (tool in categoryTools) {
        val toolName = tool.descriptor.name
        // Checked means "allowed" (not in disallow list)
        val isAllowed = toolName !in disallowedToolNames
        val toolNode = CheckedTreeNode(ToolNode(tool))
        toolNode.isChecked = isAllowed
        toolNodes[toolName] = toolNode
        categoryNode.add(toolNode)

        if (!isAllowed) allToolsAllowed = false
      }

      // Set category checkbox state based on its children
      categoryNode.isChecked = allToolsAllowed
    }

    val renderer = DisallowListTreeCellRenderer()
    val columns = arrayOf<ColumnInfo<*, *>>(
      TreeColumnInfo(McpServerBundle.message("dialog.mcp.tools.column.name")),
      ToolDescriptionColumnInfo()
    )

    val table = CheckboxTreeTable(root, renderer, columns)
    table.setRootVisible(false)
    TreeUtil.expandAll(table.tree)

    return table
  }

  override fun isModified(): Boolean {
    val currentDisallowed = getCurrentDisallowedTools()
    val disallowListModified = currentDisallowed != initialDisallowedTools
    
    val filterModified = if (Registry.`is`("mcp.server.show.advanced.filter.options.ui", false)) {
      toolsFilterTextArea?.text != initialToolsFilter
    } else {
      false
    }
    
    return disallowListModified || filterModified
  }

  override fun apply() {
    val disallowedTools = getCurrentDisallowedTools()
    McpToolDisallowListSettings.getInstance().disallowedToolNames = disallowedTools
    initialDisallowedTools = disallowedTools
    
    val filterChanged = if (Registry.`is`("mcp.server.show.advanced.filter.options.ui", false)) {
      val newFilter = toolsFilterTextArea?.text ?: ""
      val changed = newFilter != initialToolsFilter
      McpToolFilterSettings.getInstance().toolsFilter = newFilter
      initialToolsFilter = newFilter
      changed
    } else {
      false
    }
    
    // Refresh tree if filter was changed, as it affects displayed tools
    if (filterChanged) {
      refreshTreeTable()
    }
  }

  override fun reset() {
    val settings = McpToolDisallowListSettings.getInstance()
    initialDisallowedTools = settings.disallowedToolNames

    for ((toolName, node) in toolNodes) {
      // Checked means "allowed" (not in disallow list)
      node.isChecked = toolName !in initialDisallowedTools
    }

    // Update category nodes
    treeTable?.tree?.model?.root?.let { root ->
      if (root is DefaultMutableTreeNode) {
        for (i in 0 until root.childCount) {
          val categoryNode = root.getChildAt(i) as? CheckedTreeNode ?: continue
          var allChecked = true
          for (j in 0 until categoryNode.childCount) {
            val toolNode = categoryNode.getChildAt(j) as? CheckedTreeNode ?: continue
            if (!toolNode.isChecked) {
              allChecked = false
              break
            }
          }
          categoryNode.isChecked = allChecked
        }
      }
    }

    treeTable?.repaint()
    
    if (Registry.`is`("mcp.server.show.advanced.filter.options.ui", false)) {
      val filterSettings = McpToolFilterSettings.getInstance()
      initialToolsFilter = filterSettings.toolsFilter
      toolsFilterTextArea?.text = initialToolsFilter
    }
  }

  override fun disposeUIResources() {
    mainPanel = null
    treeTable = null
    treeTableScrollPane = null
    toolNodes.clear()
    toolsFilterTextArea = null
  }

  private fun refreshTreeTable() {
    val panel = mainPanel ?: return
    
    // Get tools filtered by all providers except DisallowListBasedMcpToolFilterProvider
    val tools = McpServerService.getInstance().getMcpToolsFiltered(
      excludeProviders = setOf(DisallowListBasedMcpToolFilterProvider::class.java)
    )
    
    val settings = McpToolDisallowListSettings.getInstance()
    initialDisallowedTools = settings.disallowedToolNames
    
    // Remove old tree table scroll pane
    treeTableScrollPane?.let { panel.remove(it) }
    
    // Create new tree table with updated tools
    val newTreeTable = createTreeTable(tools, initialDisallowedTools)
    val scrollPane = ScrollPaneFactory.createScrollPane(newTreeTable)
    scrollPane.preferredSize = Dimension(600, 400)
    
    panel.add(scrollPane, BorderLayout.CENTER)
    treeTable = newTreeTable
    treeTableScrollPane = scrollPane
    
    panel.revalidate()
    panel.repaint()
  }

  private fun getCurrentDisallowedTools(): Set<String> {
    val disallowed = mutableSetOf<String>()
    for ((toolName, node) in toolNodes) {
      // If not checked, it's disallowed
      if (!node.isChecked) {
        disallowed.add(toolName)
      }
    }
    return disallowed
  }

  private class CategoryNode(val category: McpToolCategory)

  private class ToolNode(val tool: McpTool)

  private class DisallowListTreeCellRenderer : CheckboxTree.CheckboxTreeCellRenderer() {
    override fun customizeRenderer(
      tree: JTree,
      value: Any,
      selected: Boolean,
      expanded: Boolean,
      leaf: Boolean,
      row: Int,
      hasFocus: Boolean
    ) {
      val node = value as? DefaultMutableTreeNode ?: return
      when (val userObject = node.userObject) {
        is CategoryNode -> {
          textRenderer.append(userObject.category.shortName, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
        }
        is ToolNode -> {
          textRenderer.append(userObject.tool.descriptor.name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
        }
      }
    }
  }

  private class ToolDescriptionColumnInfo : ColumnInfo<DefaultMutableTreeNode, String>(
    McpServerBundle.message("dialog.mcp.tools.column.description")
  ) {
    override fun valueOf(node: DefaultMutableTreeNode): String {
      return when (val userObject = node.userObject) {
        is CategoryNode -> ""
        is ToolNode -> userObject.tool.descriptor.description.trimIndent()
        else -> ""
      }
    }
  }
}
