package com.intellij.mcpserver.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.mcpserver.ClientInfo
import com.intellij.mcpserver.McpServerBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.JBColor
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SearchTextField
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.event.ListSelectionListener
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

internal class McpToolCallsPanel(
  diagnosticService: McpDiagnosticService,
  private val sessionIdFilter: String? = null,
) : JPanel(BorderLayout()), Disposable {
  private val tableModel = ToolCallsTableModel()
  private val table = JBTable(tableModel)
  private val detailPanel = ToolCallDetailPanel()

  private var allEntries: List<McpToolCallEntry> = emptyList()
  private var filterText: String = ""
  private var filterStatus: ToolCallStatus? = null
  private var autoScroll: Boolean = true

  init {
    Disposer.register(this, detailPanel)

    table.emptyText.text = McpServerBundle.message("mcp.toolwindow.calls.empty")
    table.setShowGrid(false)
    table.tableHeader.reorderingAllowed = false
    table.setDefaultRenderer(Any::class.java, StatusCellRenderer())

    table.selectionModel.addListSelectionListener(ListSelectionListener {
      if (!it.valueIsAdjusting) {
        val selectedRow = table.selectedRow
        if (selectedRow >= 0 && selectedRow < tableModel.rowCount) {
          detailPanel.showEntry(tableModel.getEntry(selectedRow))
        }
        else {
          detailPanel.clear()
        }
      }
    })

    val splitter = OnePixelSplitter(true, 0.65f)
    splitter.firstComponent = ScrollPaneFactory.createScrollPane(table)
    splitter.secondComponent = detailPanel

    val filterBar = createFilterBar()

    val toolbarGroup = DefaultActionGroup()
    toolbarGroup.add(object : AnAction(
      McpServerBundle.message("mcp.toolwindow.calls.clear"),
      null,
      AllIcons.Actions.GC,
    ) {
      override fun actionPerformed(e: AnActionEvent) {
        diagnosticService.clearToolCalls()
      }

      override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    })
    toolbarGroup.add(object : AnAction(
      McpServerBundle.message("mcp.toolwindow.calls.auto.scroll"),
      null,
      AllIcons.RunConfigurations.Scroll_down,
    ) {
      override fun actionPerformed(e: AnActionEvent) {
        autoScroll = !autoScroll
        Toggleable.setSelected(e.presentation, autoScroll)
      }

      override fun update(e: AnActionEvent) {
        Toggleable.setSelected(e.presentation, autoScroll)
      }

      override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    })

    val toolbar = ActionManager.getInstance().createActionToolbar("McpToolCalls", toolbarGroup, true)
    toolbar.targetComponent = this

    val topPanel = JPanel(BorderLayout())
    topPanel.add(toolbar.component, BorderLayout.WEST)
    topPanel.add(filterBar, BorderLayout.CENTER)

    add(topPanel, BorderLayout.NORTH)
    add(splitter, BorderLayout.CENTER)

    diagnosticService.observeToolCalls(this) { calls ->
      allEntries = calls
      applyFilter()
    }
  }

  private fun createFilterBar(): JPanel {
    val panel = JPanel()
    panel.layout = BoxLayout(panel, BoxLayout.X_AXIS)
    panel.border = JBUI.Borders.emptyLeft(4)

    val searchField = SearchTextField(false)
    searchField.addDocumentListener(object : DocumentListener {
      override fun insertUpdate(e: DocumentEvent) = onSearchChanged(searchField)
      override fun removeUpdate(e: DocumentEvent) = onSearchChanged(searchField)
      override fun changedUpdate(e: DocumentEvent) = onSearchChanged(searchField)
    })
    panel.add(searchField)
    panel.add(Box.createHorizontalStrut(8))

    val statusCombo = ComboBox<String>()
    statusCombo.addItem(McpServerBundle.message("mcp.toolwindow.calls.filter.all.statuses"))
    ToolCallStatus.entries.forEach { statusCombo.addItem(it.name) }
    statusCombo.addActionListener {
      val selected = statusCombo.selectedItem as? String
      filterStatus = if (selected == McpServerBundle.message("mcp.toolwindow.calls.filter.all.statuses")) null
      else ToolCallStatus.entries.find { it.name == selected }
      applyFilter()
    }
    panel.add(statusCombo)

    return panel
  }

  private fun onSearchChanged(searchField: SearchTextField) {
    filterText = searchField.text.trim()
    applyFilter()
  }

  private fun applyFilter() {
    val filtered = allEntries.filter { entry ->
      val matchesSession = sessionIdFilter == null || entry.sessionId == sessionIdFilter
      val matchesText = filterText.isEmpty() || entry.toolName.contains(filterText, ignoreCase = true)
      val matchesStatus = filterStatus == null || entry.status == filterStatus
      matchesSession && matchesText && matchesStatus
    }
    tableModel.updateEntries(filtered)
    if (autoScroll && filtered.isNotEmpty()) {
      SwingUtilities.invokeLater {
        val lastRow = table.rowCount - 1
        if (lastRow >= 0) {
          table.scrollRectToVisible(table.getCellRect(lastRow, 0, true))
        }
      }
    }
  }

  override fun dispose() = Unit

  private class ToolCallsTableModel : AbstractTableModel() {
    private var entries: List<McpToolCallEntry> = emptyList()

    fun updateEntries(newEntries: List<McpToolCallEntry>) {
      entries = newEntries
      fireTableDataChanged()
    }

    fun getEntry(row: Int): McpToolCallEntry? = entries.getOrNull(row)

    override fun getRowCount(): Int = entries.size

    override fun getColumnCount(): Int = COLUMN_COUNT

    override fun getColumnName(column: Int): String = when (column) {
      COLUMN_TIME -> McpServerBundle.message("mcp.toolwindow.calls.column.time")
      COLUMN_TOOL -> McpServerBundle.message("mcp.toolwindow.calls.column.tool")
      COLUMN_CLIENT -> McpServerBundle.message("mcp.toolwindow.calls.column.client")
      COLUMN_STATUS -> McpServerBundle.message("mcp.toolwindow.calls.column.status")
      COLUMN_DURATION -> McpServerBundle.message("mcp.toolwindow.calls.column.duration")
      COLUMN_PROJECT -> McpServerBundle.message("mcp.toolwindow.calls.column.project")
      else -> ""
    }

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? {
      val entry = entries.getOrNull(rowIndex) ?: return null
      return when (columnIndex) {
        COLUMN_TIME -> formatTime(entry.startTimeMs)
        COLUMN_TOOL -> entry.toolName
        COLUMN_CLIENT -> entry.clientInfo.name
        COLUMN_STATUS -> entry.status.name
        COLUMN_DURATION -> {
          val endTime = entry.endTimeMs ?: return "..."
          StringUtil.formatDuration(endTime - entry.startTimeMs)
        }
        COLUMN_PROJECT -> entry.projectName ?: ""
        else -> null
      }
    }

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false
  }

  private class StatusCellRenderer : DefaultTableCellRenderer() {
    override fun getTableCellRendererComponent(
      table: JTable,
      value: Any?,
      isSelected: Boolean,
      hasFocus: Boolean,
      row: Int,
      column: Int,
    ): Component {
      val component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
      if (column == COLUMN_STATUS && !isSelected) {
        foreground = when (value?.toString()) {
          ToolCallStatus.SUCCESS.name -> JBColor(0x59A869, 0x499C54)
          ToolCallStatus.ERROR.name -> JBColor(0xDB5C5C, 0xC7504A)
          ToolCallStatus.IN_PROGRESS.name -> JBColor.GRAY
          ToolCallStatus.CANCELLED.name -> JBColor(0xD9A343, 0xD0A750)
          else -> table.foreground
        }
      }
      else if (!isSelected) {
        foreground = table.foreground
      }
      return component
    }
  }

  companion object {
    private const val COLUMN_TIME = 0
    private const val COLUMN_TOOL = 1
    private const val COLUMN_CLIENT = 2
    private const val COLUMN_STATUS = 3
    private const val COLUMN_DURATION = 4
    private const val COLUMN_PROJECT = 5
    private const val COLUMN_COUNT = 6
  }
}

private class ToolCallDetailPanel : JPanel(BorderLayout()), Disposable {
  private val editor: EditorEx

  init {
    val document = EditorFactory.getInstance().createDocument("")
    editor = EditorFactory.getInstance().createViewer(document, null, EditorKind.PREVIEW) as EditorEx
    editor.settings.apply {
      isLineNumbersShown = false
      isFoldingOutlineShown = false
      isLineMarkerAreaShown = false
      isIndentGuidesShown = false
      additionalLinesCount = 0
      additionalColumnsCount = 0
      isUseSoftWraps = true
    }
    editor.setCaretVisible(false)

    add(editor.component, BorderLayout.CENTER)
    border = JBUI.Borders.emptyTop(2)
  }

  fun showEntry(entry: McpToolCallEntry?) {
    if (entry == null) {
      clear()
      return
    }
    val sb = StringBuilder()
    sb.appendLine("${McpServerBundle.message("mcp.toolwindow.calls.detail.parameters")}:")
    sb.appendLine(formatJson(entry.arguments))
    if (entry.errorMessage != null) {
      sb.appendLine()
      sb.appendLine("${McpServerBundle.message("mcp.toolwindow.calls.detail.error")}:")
      sb.appendLine(entry.errorMessage)
    }
    if (entry.sideEffectsCount > 0) {
      sb.appendLine()
      sb.appendLine(McpServerBundle.message("mcp.toolwindow.calls.detail.side.effects", entry.sideEffectsCount))
    }
    setText(sb.toString())
  }

  fun clear() {
    setText("")
  }

  private fun setText(text: String) {
    runWriteAction { editor.document.setText(text) }
    editor.scrollingModel.scrollTo(LogicalPosition(0, 0), ScrollType.MAKE_VISIBLE)
  }

  override fun dispose() {
    EditorFactory.getInstance().releaseEditor(editor)
  }
}

private val jsonPrinter = Json { prettyPrint = true }

private fun formatJson(json: JsonObject): String {
  return jsonPrinter.encodeToString(JsonObject.serializer(), json)
}

internal data class McpToolCallEntry(
  val callId: Int,
  val sessionId: String?,
  val toolName: String,
  val clientInfo: ClientInfo,
  val projectName: String?,
  val arguments: JsonObject,
  val startTimeMs: Long,
  val endTimeMs: Long?,
  val status: ToolCallStatus,
  val errorMessage: String?,
  val sideEffectsCount: Int,
)

internal enum class ToolCallStatus {
  IN_PROGRESS, SUCCESS, ERROR, CANCELLED,
}