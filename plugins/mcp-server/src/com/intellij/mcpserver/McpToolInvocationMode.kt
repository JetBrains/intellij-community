package com.intellij.mcpserver

/**
 * Describes the method of invoking MCP tools.
 */
enum class McpToolInvocationMode {
  /**
   * Simple MCP tool invocation (default).
   */
  DIRECT,

  /**
   * Invocation through a universal tool (tool router).
   */
  VIA_ROUTER,

  /**
   * Direct MCP tool invocation even when universal tool router is enabled.
   */
  DIRECT_WITH_ROUTER_ENABLED
}
