// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.settings

import com.intellij.mcpserver.McpServerBundle
import com.intellij.mcpserver.McpTool
import com.intellij.mcpserver.McpToolFilterProvider.McpToolState
import com.intellij.mcpserver.McpToolsMarkdownExporter
import com.intellij.mcpserver.impl.DisallowListBasedMcpToolFilterProvider
import com.intellij.mcpserver.impl.McpServerService
import com.intellij.mcpserver.settings.ui.CategoryNode
import com.intellij.mcpserver.settings.ui.McpToolNode
import com.intellij.mcpserver.settings.ui.McpToolStateColumnInfo
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.rows
import com.intellij.ui.dsl.builder.selected
import com.intellij.ui.treeStructure.treetable.ListTreeTableModelOnColumns
import com.intellij.ui.treeStructure.treetable.TreeColumnInfo
import com.intellij.ui.treeStructure.treetable.TreeTable
import com.intellij.ui.treeStructure.treetable.TreeTableCellRenderer
import com.intellij.ui.treeStructure.treetable.TreeTableModel
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.tree.TreeUtil
import org.jetbrains.annotations.NonNls
import java.awt.BorderLayout
import java.awt.Dimension
import java.nio.file.Path
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer
import javax.swing.tree.DefaultMutableTreeNode
import kotlin.io.path.writeText

/**
 * Configurable for managing the MCP tool states.
 * Displays available tools in a tree structure (grouped by category) with state selection
 * to control tool availability (On/Off/On Demand).
 */
class McpToolFilterConfigurable : SearchableConfigurable {
  private var mainPanel: JPanel? = null
  private var treeTable: TreeTable? = null
  private var treeTableScrollPane: javax.swing.JScrollPane? = null
  private val toolNodes = mutableMapOf<String, McpToolNode>()
  private var initialToolStates: Map<String, McpToolState> = emptyMap()
  private var toolsFilterTextArea: JBTextArea? = null
  private var initialToolsFilter: @NlsSafe String = ""
  private var showExperimentalCheckbox: javax.swing.JCheckBox? = null
  private var initialShowExperimental: Boolean = false

  override fun getDisplayName(): String = McpServerBundle.message("configurable.mcp.tool.filter")

  override fun getId(): @NonNls String = "com.intellij.mcpserver.settings.filter"

  override fun createComponent(): JComponent {
    val panel = JPanel(BorderLayout())

    // Get tools filtered by all providers except DisallowListBasedMcpToolFilterProvider
    val tools = McpServerService.getInstance().getMcpToolsFiltered(
      useFiltersFromEP = false,
      excludeProviders = emptySet() //setOf(DisallowListBasedMcpToolFilterProvider::class.java)
    )

    val settings = McpToolDisallowListSettings.getInstance()
    initialToolStates = settings.toolStates
    
    val filterSettings = McpToolFilterSettings.getInstance()
    initialShowExperimental = filterSettings.showExperimental

    val treeTableComponent = createTreeTable(tools, initialToolStates, initialShowExperimental)
    val scrollPane = ScrollPaneFactory.createScrollPane(treeTableComponent)
    scrollPane.preferredSize = Dimension(800, 400)

    panel.add(scrollPane, BorderLayout.CENTER)
    treeTableScrollPane = scrollPane

    // Add top panel with "Show experimental tools" checkbox
    val topPanel = panel {
      row {
        val checkbox = checkBox(McpServerBundle.message("configurable.mcp.tool.filter.show.experimental"))
          .selected(initialShowExperimental)
          .onChanged { 
            refreshTreeTable()
          }
        showExperimentalCheckbox = checkbox.component
      }
    }
    panel.add(topPanel, BorderLayout.NORTH)

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

  private fun createTreeTable(tools: List<McpTool>, toolStates: Map<String, McpToolState>, showExperimental: Boolean = false): TreeTable {
    val root = DefaultMutableTreeNode("Root")
    toolNodes.clear()

    val toolsByCategory = tools
      .groupBy { it.descriptor.category }
      .toSortedMap(compareBy(String.CASE_INSENSITIVE_ORDER) { it.shortName })
      .mapValues { (_, categoryTools) -> categoryTools.sortedBy { it.descriptor.name.lowercase() } }
      .filterKeys { category -> showExperimental || !category.isExperimental }

    for ((category, categoryTools) in toolsByCategory) {
      val categoryNode = DefaultMutableTreeNode(CategoryNode(category.shortName))
      root.add(categoryNode)

      for (tool in categoryTools) {
        val toolName = tool.descriptor.name
        val state = toolStates.getOrDefault(toolName, McpToolState.ON_DEMAND)
        val mcpToolNode = McpToolNode(
          toolName = toolName,
          toolDescription = tool.descriptor.description.trimIndent(),
          state = state
        )
        toolNodes[toolName] = mcpToolNode
        categoryNode.add(DefaultMutableTreeNode(mcpToolNode))
      }
    }

    @Suppress("UNCHECKED_CAST")
    val columns = arrayOf(
      TreeColumnInfo(McpServerBundle.message("dialog.mcp.tools.column.name")),
      ToolDescriptionColumnInfo(),
      McpToolStateColumnInfo()
    ) as Array<ColumnInfo<Any, Any>>

    val model = ListTreeTableModelOnColumns(root, columns)
    val table = object : TreeTable(model) {
      override fun createTableRenderer(treeTableModel: TreeTableModel): TreeTableCellRenderer {
        val renderer = super.createTableRenderer(treeTableModel)
        renderer.setRootVisible(false)
        renderer.setShowsRootHandles(true)
        return renderer
      }
      
      override fun getCellRenderer(row: Int, column: Int): TableCellRenderer {
        val treePath = tree.getPathForRow(row) ?: return super.getCellRenderer(row, column)
        val node = treePath.lastPathComponent
        return columns[column].getRenderer(node) ?: super.getCellRenderer(row, column)
      }
      
      override fun getCellEditor(row: Int, column: Int): TableCellEditor {
        val treePath = tree.getPathForRow(row) ?: return super.getCellEditor(row, column)
        val node = treePath.lastPathComponent
        return columns[column].getEditor(node) ?: super.getCellEditor(row, column)
      }
    }
    
    table.setRootVisible(false)
    TreeUtil.expandAll(table.tree)

    return table
  }

  override fun isModified(): Boolean {
    val currentStates = getCurrentToolStates()
    val statesModified = currentStates != initialToolStates
    
    val showExperimentalModified = (showExperimentalCheckbox?.isSelected ?: false) != initialShowExperimental
    
    val filterModified = if (Registry.`is`("mcp.server.show.advanced.filter.options.ui", false)) {
      toolsFilterTextArea?.text != initialToolsFilter
    } else {
      false
    }
    
    return statesModified || filterModified || showExperimentalModified
  }

  override fun apply() {
    val toolStates = getCurrentToolStates()
    McpToolDisallowListSettings.getInstance().toolStates = toolStates
    initialToolStates = toolStates
    
    val newShowExperimental = showExperimentalCheckbox?.isSelected ?: false
    val showExperimentalChanged = newShowExperimental != initialShowExperimental
    McpToolFilterSettings.getInstance().showExperimental = newShowExperimental
    initialShowExperimental = newShowExperimental
    
    val filterChanged = if (Registry.`is`("mcp.server.show.advanced.filter.options.ui", false)) {
      val newFilter = toolsFilterTextArea?.text ?: ""
      val changed = newFilter != initialToolsFilter
      McpToolFilterSettings.getInstance().toolsFilter = newFilter
      initialToolsFilter = newFilter
      changed
    } else {
      false
    }
    
    // Refresh tree if filter or show experimental was changed, as it affects displayed tools
    if (filterChanged || showExperimentalChanged) {
      refreshTreeTable()
    }
  }

  override fun reset() {
    val settings = McpToolDisallowListSettings.getInstance()
    initialToolStates = settings.toolStates

    for ((toolName, node) in toolNodes) {
      val state = initialToolStates.getOrDefault(toolName, McpToolState.ON_DEMAND)
      node.state = state
    }

    treeTable?.repaint()
    
    val filterSettings = McpToolFilterSettings.getInstance()
    initialShowExperimental = filterSettings.showExperimental
    showExperimentalCheckbox?.isSelected = initialShowExperimental
    
    if (Registry.`is`("mcp.server.show.advanced.filter.options.ui", false)) {
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
    showExperimentalCheckbox = null
  }

  private fun refreshTreeTable() {
    val panel = mainPanel ?: return
    
    // Get tools filtered by all providers except DisallowListBasedMcpToolFilterProvider
    val tools = McpServerService.getInstance().getMcpToolsFiltered(
      excludeProviders = setOf(DisallowListBasedMcpToolFilterProvider::class.java)
    )
    
    val settings = McpToolDisallowListSettings.getInstance()
    initialToolStates = settings.toolStates
    
    val currentShowExperimental = showExperimentalCheckbox?.isSelected ?: false
    
    // Remove old tree table scroll pane
    treeTableScrollPane?.let { panel.remove(it) }
    
    // Create new tree table with updated tools
    val newTreeTable = createTreeTable(tools, initialToolStates, currentShowExperimental)
    val scrollPane = ScrollPaneFactory.createScrollPane(newTreeTable)
    scrollPane.preferredSize = Dimension(800, 400)
    
    panel.add(scrollPane, BorderLayout.CENTER)
    treeTable = newTreeTable
    treeTableScrollPane = scrollPane
    
    panel.revalidate()
    panel.repaint()
  }

  private fun getCurrentToolStates(): Map<String, McpToolState> {
    val states = mutableMapOf<String, McpToolState>()
    for ((toolName, node) in toolNodes) {
      states[toolName] = node.state
    }
    return states
  }

  private class ToolDescriptionColumnInfo : ColumnInfo<DefaultMutableTreeNode, String>(
    McpServerBundle.message("dialog.mcp.tools.column.description")
  ) {
    override fun valueOf(node: DefaultMutableTreeNode): String {
      return when (val userObject = node.userObject) {
        is CategoryNode -> ""
        is McpToolNode -> userObject.toolDescription
        else -> ""
      }
    }
  }
}
