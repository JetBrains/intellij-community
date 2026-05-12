package com.intellij.mcpserver.widget

import com.intellij.mcpserver.McpServerBundle
import com.intellij.mcpserver.impl.McpServerService
import com.intellij.mcpserver.settings.McpServerSettings
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetSettings
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager
import com.intellij.platform.ide.productMode.IdeProductMode
import kotlinx.coroutines.launch

internal class McpServerStatusBarWidgetFactory : StatusBarWidgetFactory {
  companion object {
    const val WIDGET_ID: String = "McpServerStatusBarWidget"
  }

  override fun getId(): String = WIDGET_ID

  override fun getDisplayName(): String = McpServerBundle.message("mcp.server.status.bar.widget.name")

  override fun isEnabledByDefault(): Boolean = McpServerSettings.getInstance().state.enableMcpServer

  override fun isAvailable(project: Project): Boolean = IdeProductMode.isMonolith

  override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true

  override fun createWidget(project: Project): StatusBarWidget = McpServerStatusBarWidget(project)

  override fun disposeWidget(widget: StatusBarWidget) {
    Disposer.dispose(widget)
  }
}

/**
 * Enables the MCP status bar widget, unless the user has explicitly disabled it before.
 * Runs asynchronously in the background.
 */
fun enableIfNotExplicitlyDisabled() {
  McpServerService.getInstance().cs.launch {
    if (StatusBarWidgetSettings.getInstance().isExplicitlyDisabled(McpServerStatusBarWidgetFactory.WIDGET_ID)) return@launch
    val factory = StatusBarWidgetFactory.EP_NAME.findExtension(McpServerStatusBarWidgetFactory::class.java) ?: return@launch
    StatusBarWidgetSettings.getInstance().setEnabled(factory, true)
    for (project in ProjectManagerEx.getOpenProjects()) {
      project.service<StatusBarWidgetsManager>().updateWidget(factory)
    }
  }
}