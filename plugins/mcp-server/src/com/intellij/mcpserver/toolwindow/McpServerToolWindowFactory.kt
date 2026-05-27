package com.intellij.mcpserver.toolwindow

import com.intellij.mcpserver.McpServerBundle
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class McpServerToolWindowFactory : ToolWindowFactory, DumbAware {
  override fun shouldBeAvailable(project: Project): Boolean = false

  override suspend fun manage(toolWindow: ToolWindow, toolWindowManager: ToolWindowManager) {
    val dataService = serviceAsync<McpDiagnosticService>()
    dataService.collectAvailability { hasActive ->
      withContext(Dispatchers.EDT) {
        toolWindow.isAvailable = hasActive
      }
    }
  }

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val contentManager = toolWindow.contentManager
    val contentFactory = contentManager.factory
    val diagnosticService = service<McpDiagnosticService>()

    val sessionsPanel = McpSessionsPanel(diagnosticService)
    val sessionsContent = contentFactory.createContent(sessionsPanel, McpServerBundle.message("mcp.toolwindow.tab.sessions"), false)
    contentManager.addContent(sessionsContent)

    val toolCallsPanel = McpToolCallsPanel(diagnosticService)
    val toolCallsContent = contentFactory.createContent(toolCallsPanel, McpServerBundle.message("mcp.toolwindow.tab.tool.calls"), false)
    contentManager.addContent(toolCallsContent)

    val configPanel = McpConfigurationPanel(project)
    val configContent = contentFactory.createContent(configPanel, McpServerBundle.message("mcp.toolwindow.tab.configuration"),false)
    contentManager.addContent(configContent)
  }
}