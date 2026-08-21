package com.intellij.mcpserver.widget

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBarWidget
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

/**
 * Lets a product replace the MCP status-bar widget while preserving the platform factory, widget id,
 * and user visibility setting.
 *
 * Implementations should return `null` when they do not apply to the current product or project.
 */
@ApiStatus.Internal
interface McpServerStatusBarWidgetProvider {
  fun getDisplayName(): @Nls String? = null

  fun isEnabledByDefault(): Boolean? = null

  fun createWidget(project: Project): StatusBarWidget?

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<McpServerStatusBarWidgetProvider> =
      ExtensionPointName.create("com.intellij.mcpServer.statusBarWidgetProvider")
  }
}
