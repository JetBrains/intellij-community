package com.intellij.mcpserver.toolwindow

import com.intellij.mcpserver.McpServerBundle
import com.intellij.mcpserver.impl.McpServerService
import com.intellij.mcpserver.settings.McpServerSettings
import com.intellij.mcpserver.settings.McpServerSettingsConfigurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.BorderLayout.NORTH
import javax.swing.JPanel

internal class McpConfigurationPanel(private val project: Project) : JPanel(BorderLayout()) {
  init {
    border = JBUI.Borders.empty(8)

    val serverService = McpServerService.getInstance()
    val settings = McpServerSettings.getInstance()

    val isRunning = serverService.isRunning
    val statusText = if (isRunning)
      McpServerBundle.message("mcp.toolwindow.config.server.running")
    else
      McpServerBundle.message("mcp.toolwindow.config.server.stopped")

    add(panel {
      group(McpServerBundle.message("mcp.toolwindow.title")) {
        row {
          label(McpServerBundle.message("mcp.toolwindow.config.server.status", statusText))
        }
        if (isRunning) {
          row {
            label("SSE: ${serverService.serverSseUrl}") //NON-NLS
          }
          row {
            label("HTTP Stream: ${serverService.serverStreamUrl}") //NON-NLS
          }
        }
        row {
          checkBox(McpServerBundle.message("checkbox.enable.brave.mode.skip.command.execution.confirmations"))
            .applyToComponent {
              this.isSelected = settings.state.enableBraveMode
              addActionListener {
                settings.state.enableBraveMode = this.isSelected
              }
            }
        }
      }
      group {
        row {
          link(McpServerBundle.message("mcp.toolwindow.config.open.settings")) { //NON-NLS
            ShowSettingsUtil.getInstance().showSettingsDialog(project, McpServerSettingsConfigurable::class.java)
          }
        }
      }
    }, NORTH)
  }
}