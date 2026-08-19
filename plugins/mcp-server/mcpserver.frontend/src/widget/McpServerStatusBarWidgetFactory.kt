package com.intellij.mcpserver.frontend.widget

import com.intellij.mcpserver.McpServerBundle
import com.intellij.mcpserver.settings.McpServerSettings
import com.intellij.mcpserver.util.MCP_STATUS_BAR_WIDGET_ID
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.platform.ide.productMode.IdeProductMode

internal class McpServerStatusBarWidgetFactory : StatusBarWidgetFactory {
  companion object {
    const val WIDGET_ID: String = MCP_STATUS_BAR_WIDGET_ID
  }

  override fun getId(): String = WIDGET_ID

  override fun getDisplayName(): String = McpServerBundle.message("mcp.server.status.bar.widget.name")

  override fun isEnabledByDefault(): Boolean = McpServerSettings.getInstance().enableMcpServer

  override fun isAvailable(project: Project): Boolean = IdeProductMode.isMonolith

  override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true

  override fun createWidget(project: Project): StatusBarWidget = McpServerStatusBarWidget(project)

  override fun disposeWidget(widget: StatusBarWidget) {
    Disposer.dispose(widget)
  }
}

