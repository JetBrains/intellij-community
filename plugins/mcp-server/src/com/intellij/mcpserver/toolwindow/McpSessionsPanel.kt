package com.intellij.mcpserver.toolwindow

import com.intellij.mcpserver.ClientInfo
import com.intellij.mcpserver.McpServerBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.table.JBTable
import com.intellij.util.text.DateFormatUtil
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.Timer
import javax.swing.table.AbstractTableModel

internal class McpSessionsPanel(diagnosticService: McpDiagnosticService) : JPanel(BorderLayout()), Disposable {
  private val tableModel = SessionsTableModel()
  private val table = JBTable(tableModel)
  private val durationRefreshTimer: Timer

  init {
    table.emptyText.text = McpServerBundle.message("mcp.toolwindow.sessions.empty")
    table.setShowGrid(false)
    table.tableHeader.reorderingAllowed = false

    val scrollPane = ScrollPaneFactory.createScrollPane(table)
    add(scrollPane, BorderLayout.CENTER)

    diagnosticService.observeSessions(this) { sessions ->
      tableModel.updateSessions(sessions)
    }

    durationRefreshTimer = Timer(1000) {
      if (tableModel.rowCount > 0) {
        tableModel.fireTableColumnUpdated(COLUMN_DURATION)
      }
    }
    durationRefreshTimer.start()
  }

  override fun dispose() {
    durationRefreshTimer.stop()
  }

  private class SessionsTableModel : AbstractTableModel() {
    private var sessions: List<McpSessionInfo> = emptyList()

    fun updateSessions(newSessions: List<McpSessionInfo>) {
      sessions = newSessions
      fireTableDataChanged()
    }

    override fun getRowCount(): Int = sessions.size

    override fun getColumnCount(): Int = COLUMN_COUNT

    override fun getColumnName(column: Int): String = when (column) {
      COLUMN_CLIENT -> McpServerBundle.message("mcp.toolwindow.sessions.column.client")
      COLUMN_VERSION -> McpServerBundle.message("mcp.toolwindow.sessions.column.version")
      COLUMN_TRANSPORT -> McpServerBundle.message("mcp.toolwindow.sessions.column.transport")
      COLUMN_DURATION -> McpServerBundle.message("mcp.toolwindow.sessions.column.duration")
      else -> ""
    }

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? {
      val session = sessions.getOrNull(rowIndex) ?: return null
      return when (columnIndex) {
        COLUMN_CLIENT -> session.clientInfo?.name ?: "..."
        COLUMN_VERSION -> session.clientInfo?.version ?: "..."
        COLUMN_TRANSPORT -> session.transportType.displayName()
        COLUMN_DURATION -> StringUtil.formatDuration(System.currentTimeMillis() - session.startTimeMs)
        else -> null
      }
    }

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false

    fun fireTableColumnUpdated(column: Int) {
      for (row in 0 until rowCount) {
        fireTableCellUpdated(row, column)
      }
    }
  }

  companion object {
    private const val COLUMN_CLIENT = 0
    private const val COLUMN_VERSION = 1
    private const val COLUMN_TRANSPORT = 2
    private const val COLUMN_DURATION = 3
    private const val COLUMN_COUNT = 4
  }
}

private fun TransportType.displayName(): String = when (this) {
  TransportType.SSE -> "SSE"
  TransportType.STREAMABLE_HTTP -> "HTTP Stream"
  TransportType.STDIO -> "Stdio"
}

internal fun formatTime(timeMs: Long): String {
  return DateFormatUtil.formatTimeWithSeconds(timeMs)
}

internal data class McpSessionInfo(
  val sessionId: String,
  val clientInfo: ClientInfo?,
  val transportType: TransportType,
  val startTimeMs: Long,
  val localAgentId: String?,
)

internal enum class TransportType {
  SSE,
  STREAMABLE_HTTP,
  STDIO,
}