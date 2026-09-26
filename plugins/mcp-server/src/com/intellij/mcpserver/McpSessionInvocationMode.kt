package com.intellij.mcpserver

/**
 * Describes the method of invoking MCP tools at the session level.
 */
enum class McpSessionInvocationMode {
  /**
   * Direct MCP tool invocation - tools are called directly without routing.
   */
  DIRECT,

  /**
   * Invocation through a universal tool (tool router).
   * The router tool and exception tools are exposed directly,
   * while other tools are invoked via the router.
   */
  VIA_ROUTER,
}
