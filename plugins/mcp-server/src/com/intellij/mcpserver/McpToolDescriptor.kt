package com.intellij.mcpserver

import com.intellij.openapi.util.NlsSafe
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import org.jetbrains.annotations.Nls

class McpToolDescriptor(
  /**
   * Tool name
   */
  val name: @NlsSafe String,

  /**
   * Tool title
   */
  val title: @NlsSafe String? = null,

  /**
   * Tool description (agent-facing, from `@McpDescription`)
   */
  val description: @NlsSafe String,

  /**
   * Optional plugin-provided human-readable description for this tool (see [McpToolset.displayDescription]).
   * When `null`, [presentableDescription] falls back to the agent-facing [description].
   */
  val displayDescription: @Nls String? = null,

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
  val annotations: ToolAnnotations? = null,
)

/**
 * The description to present in the UI: the plugin-provided [McpToolDescriptor.displayDescription] when available,
 * otherwise the agent-facing [McpToolDescriptor.description].
 */
val McpToolDescriptor.presentableDescription: @Nls String
  get() = displayDescription ?: description