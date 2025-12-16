package com.intellij.mcpserver

/**
 * Filter for selecting which MCP tools should be exposed in a server session.
 *
 * Different filter implementations allow for flexible tool selection strategies.
 * Filters are applied when registering tools for an MCP server session, allowing
 * different agents to see different subsets of available tools.
 */
sealed interface McpToolFilter {
  /**
   * Checks if a tool with the given name should be included in the session.
   *
   * @param toolName the name of the tool to check
   * @return true if the tool should be included, false otherwise
   */
  fun shouldInclude(toolName: String): Boolean

  /**
   * Allow-all filter: all tools are included.
   *
   * This is the default filter when no restrictions are needed.
   * Use this instead of null to explicitly indicate no filtering.
   */
  data object AllowAll : McpToolFilter {
    override fun shouldInclude(toolName: String): Boolean = true
  }

  /**
   * Allow-list filter: only tools with names in the allowed set are included.
   *
   * This is useful for exposing a specific subset of tools to an agent,
   * for example, limiting an agent to only file operations.
   *
   * @property allowedTools set of tool names that should be included
   */
  data class AllowList(val allowedTools: Set<String>) : McpToolFilter {
    override fun shouldInclude(toolName: String): Boolean = toolName in allowedTools
  }
}
