package com.intellij.mcpserver.annotations

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialInfo

@Target(AnnotationTarget.FUNCTION)
annotation class McpTool(
  /**
   * Custom name for the tool. Function/method name will be used if not specified.
   */
  val name: String = ""
)

@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.PROPERTY, AnnotationTarget.TYPE)
annotation class McpDescription(val description: String)