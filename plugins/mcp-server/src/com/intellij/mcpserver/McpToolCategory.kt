package com.intellij.mcpserver

import com.intellij.openapi.util.NlsSafe

/**
 * Represents a category for MCP tools.
 *
 * @property shortName Simple name of the class where the tool is declared (e.g., "TextToolset")
 * @property fullyQualifiedName Fully qualified name of the class including package (e.g., "com.intellij.mcpserver.toolsets.general.TextToolset")
 * @property isExperimental Whether this category contains experimental tools
 * @property alwaysIncluded Whether this category contains tools that should always be included as directly accessible MCP tools
 */
data class McpToolCategory(
  val shortName: @NlsSafe String,
  val fullyQualifiedName: @NlsSafe String,
  val isExperimental: Boolean = false,
  val alwaysIncluded: Boolean = false,
)
