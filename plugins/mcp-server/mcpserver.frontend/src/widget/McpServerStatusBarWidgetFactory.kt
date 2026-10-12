package com.intellij.mcpserver.frontend.widget

import com.intellij.mcpserver.McpServerBundle
import com.intellij.mcpserver.util.MCP_STATUS_BAR_WIDGET_ID
import com.intellij.mcpserver.widget.McpServerStatusBarWidgetProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.platform.ide.productMode.IdeProductMode
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope

internal const val MCP_WIDGET_ID: String = MCP_STATUS_BAR_WIDGET_ID

internal class McpServerStatusBarWidgetFactory : StatusBarWidgetFactory {
  override fun getId(): String = MCP_WIDGET_ID

  override fun getDisplayName(): String =
    McpServerStatusBarWidgetProvider.EP_NAME.computeSafeIfAny(McpServerStatusBarWidgetProvider::getDisplayName)
    ?: McpServerBundle.message("mcp.server.status.bar.widget.name")

  override fun isEnabledByDefault(): Boolean =
    McpServerStatusBarWidgetProvider.EP_NAME.computeSafeIfAny(McpServerStatusBarWidgetProvider::isEnabledByDefault)
    ?: true

  override fun isAvailable(project: Project): Boolean = IdeProductMode.isMonolith

  override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true

  override fun createWidget(project: Project, scope: CoroutineScope): StatusBarWidget {
    return McpServerStatusBarWidgetProvider.EP_NAME.computeSafeIfAny { it.createWidget(project) }
           ?: McpServerStatusBarWidget(project, scope.childScope("McpServerStatusBarWidget"))
  }

  override fun disposeWidget(widget: StatusBarWidget) {
    Disposer.dispose(widget)
  }
}
