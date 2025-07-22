package com.intellij.mcpserver

class McpToolDescriptor(
  /**
   * Tool name
   */
  val name: String,

  /**
   * Tool description
   */
  val description: String,

  /**
   * Input schema for the tool
   */
  val inputSchema: McpToolSchema,
  val outputSchema: McpToolSchema? = null,
)