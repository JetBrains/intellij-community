package com.intellij.mcpserver

import com.intellij.mcpserver.settings.McpToolFilterSettings
import com.intellij.openapi.options.advanced.AdvancedSettings

/**
 * Sets the advanced setting that shows the router controls and returns a handle that restores the previous value.
 */
internal fun setRouterControls(enabled: Boolean): AutoCloseable {
  val id = McpToolFilterSettings.ROUTER_CONTROLS_ADVANCED_SETTING_ID
  val previous = AdvancedSettings.getBoolean(id)
  AdvancedSettings.setBoolean(id, enabled)
  return AutoCloseable { AdvancedSettings.setBoolean(id, previous) }
}
