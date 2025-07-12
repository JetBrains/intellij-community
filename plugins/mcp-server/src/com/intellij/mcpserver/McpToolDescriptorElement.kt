package com.intellij.mcpserver

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

data class McpToolDescriptorElement(val mcpToolDescriptor: McpToolDescriptor?) : AbstractCoroutineContextElement(Key) {
  companion object Key : CoroutineContext.Key<McpToolDescriptorElement>
}

val CoroutineContext.currentToolDescriptorOrNull: McpToolDescriptor? get() = get(McpToolDescriptorElement)?.mcpToolDescriptor
val CoroutineContext.currentToolDescriptor: McpToolDescriptor get() = get(McpToolDescriptorElement)?.mcpToolDescriptor
                                                                       ?: error("currentToolDescriptor called outside of a MCP tool call")
