package com.intellij.mcpserver

import com.intellij.openapi.util.NlsSafe

/**
 * Represents a category for MCP tools.
 *
 * @property shortName Simple name of the class where the tool is declared (e.g., "TextToolset")
 * @property fullyQualifiedName Fully qualified name of the class including package (e.g., "com.intellij.mcpserver.toolsets.general.TextToolset")
 */
data class McpToolCategory(
  val shortName: @NlsSafe String,
  val fullyQualifiedName: @NlsSafe String,
)
