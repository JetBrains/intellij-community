package com.intellij.mcpserver.actions

import com.intellij.mcpserver.McpServerBundle
import com.intellij.mcpserver.McpTool
import com.intellij.mcpserver.McpToolCategory
import com.intellij.mcpserver.McpToolsMarkdownExporter
import com.intellij.mcpserver.impl.McpServerService
import com.intellij.mcpserver.settings.McpToolFilterOptimizer
import com.intellij.mcpserver.settings.McpToolFilterSettings
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.CheckboxTree
import com.intellij.ui.CheckboxTreeTable
import com.intellij.ui.CheckedTreeNode
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.treeStructure.treetable.TreeColumnInfo
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.tree.TreeUtil
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.nio.file.Path
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import kotlin.io.path.writeText

class ShowMcpToolsAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    // Get all tools without filtering to show the complete list with current filter state
    val allMcpTools = McpServerService.getInstance().getMcpTools(useFiltersFromEP = false)
    // Get currently enabled tools to determine initial checkbox state
    val enabledTools = McpServerService.getInstance().getMcpTools().map { it.descriptor.fullyQualifiedName }.toSet()
    McpToolsDialog(project, allMcpTools, enabledTools).show()
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = Registry.`is`("mcp.server.show.advanced.filter.options.ui")
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

private class CategoryNode(val category: McpToolCategory)

private class McpToolsDialog(
  private val project: Project,
  tools: List<McpTool>,
  private val initiallyEnabledTools: Set<String>
) : DialogWrapper(project) {
  
  private val toolsByCategory: Map<McpToolCategory, List<McpTool>> = tools
    .groupBy { it.descriptor.category }
    .toSortedMap(compareBy(String.CASE_INSENSITIVE_ORDER) { it.shortName })
    .mapValues { (_, categoryTools) -> categoryTools.sortedBy { it.descriptor.name.lowercase() } }

  private lateinit var treeTable: CheckboxTreeTable
  private val categoryNodes = mutableMapOf<McpToolCategory, CheckedTreeNode>()
  private val toolNodes = mutableMapOf<McpTool, CheckedTreeNode>()

  init {
    title = McpServerBundle.message("dialog.mcp.tools.title")
    init()
  }

  override fun createCenterPanel(): JComponent {
    val root = CheckedTreeNode("Root")

    for ((category, categoryTools) in toolsByCategory) {
      val categoryNode = CheckedTreeNode(CategoryNode(category))
      categoryNodes[category] = categoryNode
      root.add(categoryNode)
      
      var anyToolEnabled = false
      
      for (tool in categoryTools) {
        val isEnabled = initiallyEnabledTools.contains(tool.descriptor.fullyQualifiedName)
        val toolNode = CheckedTreeNode(tool)
        toolNode.isChecked = isEnabled
        toolNodes[tool] = toolNode
        categoryNode.add(toolNode)
        
        if (isEnabled) anyToolEnabled = true
      }
      
      // Set category checkbox state based on its children
      categoryNode.isChecked = anyToolEnabled
    }

    val renderer = McpToolTreeCellRenderer()
    val columns = arrayOf<ColumnInfo<*, *>>(
      TreeColumnInfo(McpServerBundle.message("dialog.mcp.tools.column.name")),
      DescriptionColumnInfo()
    )
    
    treeTable = CheckboxTreeTable(root, renderer, columns)
    treeTable.setRootVisible(false)
    TreeUtil.expandAll(treeTable.tree)

    val scrollPane = ScrollPaneFactory.createScrollPane(treeTable)
    scrollPane.preferredSize = Dimension(800, 500)
    return scrollPane
  }

  override fun createActions(): Array<Action> {
    val exportAction = object : AbstractAction(McpServerBundle.message("dialog.mcp.tools.export.button")) {
      override fun actionPerformed(e: ActionEvent?) {
        exportToMarkdown()
      }
    }
    return arrayOf(exportAction, okAction, cancelAction)
  }

  override fun doOKAction() {
    saveFilterSettings()
    super.doOKAction()
  }

  private fun saveFilterSettings() {
    val enabledTools = mutableSetOf<String>()
    val disabledTools = mutableSetOf<String>()
    
    for ((tool, node) in toolNodes) {
      if (node.isChecked) {
        enabledTools.add(tool.descriptor.fullyQualifiedName)
      } else {
        disabledTools.add(tool.descriptor.fullyQualifiedName)
      }
    }
    
    val filter = buildOptimizedFilter(enabledTools, disabledTools)
    McpToolFilterSettings.getInstance().toolsFilter = filter
  }

  private fun buildOptimizedFilter(enabledTools: Set<String>, disabledTools: Set<String>): String {
    val categoriesInfo = toolsByCategory.map { (category, tools) ->
      McpToolFilterOptimizer.CategoryToolsInfo(
        category = category,
        toolFqns = tools.map { it.descriptor.fullyQualifiedName }.toSet()
      )
    }
    return McpToolFilterOptimizer.buildOptimizedFilter(enabledTools, disabledTools, categoriesInfo)
  }

  private fun exportToMarkdown() {
    val descriptor = FileSaverDescriptor(
      McpServerBundle.message("dialog.mcp.tools.export.title"),
      McpServerBundle.message("dialog.mcp.tools.export.description"),
      "md"
    )
    val saveDialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)
    val fileWrapper = saveDialog.save(null as Path?, "mcp_tools.md") ?: return

    val markdown = McpToolsMarkdownExporter.generateMarkdown(toolsByCategory)
    val virtualFile = fileWrapper.getVirtualFile(true) ?: return
    virtualFile.toNioPath().writeText(markdown)
  }
}

private class McpToolTreeCellRenderer : CheckboxTree.CheckboxTreeCellRenderer() {
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
      is McpTool -> {
        textRenderer.append(userObject.descriptor.name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
      }
    }
  }
}

private class DescriptionColumnInfo : ColumnInfo<DefaultMutableTreeNode, String>(
  McpServerBundle.message("dialog.mcp.tools.column.description")
) {
  override fun valueOf(node: DefaultMutableTreeNode): String {
    return when (val userObject = node.userObject) {
      is CategoryNode -> ""
      is McpTool -> userObject.descriptor.description.trimIndent()
      else -> ""
    }
  }
}
