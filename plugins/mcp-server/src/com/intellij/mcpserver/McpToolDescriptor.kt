package com.intellij.mcpserver

import com.intellij.openapi.util.NlsSafe

class McpToolDescriptor(
  /**
   * Tool name
   */
  val name: @NlsSafe String,

  /**
   * Tool description
   */
  val description: @NlsSafe String,

  /**
   * Tool category, only for UI and filtering purposes
   */
  val category: McpToolCategory,

  val fullyQualifiedName: @NlsSafe String,

  /**
   * Input schema for the tool
   */
  val inputSchema: McpToolSchema,
  val outputSchema: McpToolSchema? = null,
)