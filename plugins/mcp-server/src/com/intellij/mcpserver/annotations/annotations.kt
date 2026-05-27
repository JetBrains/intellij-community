package com.intellij.mcpserver.annotations

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialInfo

@Target(AnnotationTarget.FUNCTION)
annotation class McpTool(
  /**
   * Custom name for the tool. Function/method name will be used if not specified.
   */
  val name: String = "",

  /**
   * Optional human-readable title for the tool. If empty, a null value will be passed to protocol.
   */
  val title: String = "",
)

enum class McpToolHintValue {
  UNSPECIFIED,
  TRUE,
  FALSE,
}

@Target(AnnotationTarget.FUNCTION)
annotation class McpToolHints(
  /**
   * Whether the tool does not modify its environment.
   */
  val readOnlyHint: McpToolHintValue = McpToolHintValue.UNSPECIFIED,

  /**
   * Whether the tool performs destructive updates.
   */
  val destructiveHint: McpToolHintValue = McpToolHintValue.UNSPECIFIED,

  /**
   * Whether repeated calls with the same arguments have no additional effect.
   */
  val idempotentHint: McpToolHintValue = McpToolHintValue.UNSPECIFIED,

  /**
   * Whether the tool may interact with an open world of external entities.
   */
  val openWorldHint: McpToolHintValue = McpToolHintValue.UNSPECIFIED,
)

@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.PROPERTY, AnnotationTarget.TYPE)
annotation class McpDescription(val description: String)