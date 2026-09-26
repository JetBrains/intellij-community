package com.intellij.mcpserver.impl

import com.intellij.ide.util.PropertiesComponent
import org.jetbrains.annotations.TestOnly

const val MCP_SERVER_TERMINAL_PROMOTION_DISMISSED_KEY: String = "mcp.server.terminal.promotion.dismissed"

object McpServerTerminalPromotionDismissalState {
  fun isDismissed(): Boolean {
    return PropertiesComponent.getInstance().isTrueValue(MCP_SERVER_TERMINAL_PROMOTION_DISMISSED_KEY)
  }

  fun dismiss() {
    PropertiesComponent.getInstance().setValue(MCP_SERVER_TERMINAL_PROMOTION_DISMISSED_KEY, true)
  }

  fun showAgain() {
    PropertiesComponent.getInstance().unsetValue(MCP_SERVER_TERMINAL_PROMOTION_DISMISSED_KEY)
  }

  @TestOnly
  fun reset() {
    showAgain()
  }
}